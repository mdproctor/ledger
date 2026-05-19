package io.casehub.ledger.runtime.service;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.smallrye.mutiny.Uni;

import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.ReactiveLedgerEntryRepository;
import io.casehub.ledger.runtime.service.model.CompromisedWindow;
import io.casehub.ledger.runtime.service.model.VerificationResult;

/**
 * Reactive-tier CDI bean for agent signature verification.
 *
 * <p>
 * Present only when {@code casehub.ledger.reactive.enabled=true} — excluded by
 * {@code LedgerProcessor} otherwise. Consumers on a JDBC-only datasource must not
 * depend on this bean.
 *
 * <p>
 * The cryptographic verification logic ({@link #verifyCryptographic}) is duplicated
 * from {@link LedgerVerificationService} pending decomposition at casehubio/ledger#93.
 */
@ApplicationScoped
public class ReactiveLedgerVerificationService {

    private static final Logger LOG = Logger.getLogger(ReactiveLedgerVerificationService.class);

    @Inject
    ReactiveLedgerEntryRepository reactiveLedgerRepo;

    @Inject
    ReactiveKeyRotationService reactiveKeyRotationService;

    @Inject
    Event<AgentSignatureSuspectEvent> suspectEvent;

    /**
     * Reactive variant of {@link LedgerVerificationService#verifyAgentSignature(UUID)}.
     *
     * <p>
     * Uses {@link ReactiveLedgerEntryRepository} for the entry lookup and
     * {@link ReactiveKeyRotationService#compromisedWindowsAsync} for the compromise
     * window check. Fires {@link AgentSignatureSuspectEvent} asynchronously via
     * {@code event.fireAsync()} when the result is {@link VerificationResult#SUSPECT}.
     *
     * @param entryId the entry to verify
     * @return a {@link Uni} completing with UNSIGNED, VALID, INVALID, or SUSPECT
     * @throws IllegalArgumentException if the entry does not exist
     */
    public Uni<VerificationResult> verifyAgentSignatureAsync(final UUID entryId) {
        return reactiveLedgerRepo.findEntryById(entryId)
                .map(opt -> opt.orElseThrow(
                        () -> new IllegalArgumentException("Entry not found: " + entryId)))
                .chain(entry -> {
                    if (entry.agentSignature == null) {
                        return Uni.createFrom().item(VerificationResult.UNSIGNED);
                    }

                    final VerificationResult cryptoResult = verifyCryptographic(entry);
                    if (cryptoResult != VerificationResult.VALID) {
                        return Uni.createFrom().item(cryptoResult);
                    }

                    if (entry.agentKeyRef == null || entry.actorId == null) {
                        return Uni.createFrom().item(VerificationResult.VALID);
                    }

                    return compromisedEffectiveSinceAsync(entry.actorId, entry.agentKeyRef, entry.occurredAt)
                            .chain(effectiveSince -> {
                                if (effectiveSince.isPresent()) {
                                    return Uni.createFrom().completionStage(
                                            () -> suspectEvent.fireAsync(new AgentSignatureSuspectEvent(
                                                    entryId, entry.actorId, entry.agentKeyRef,
                                                    entry.occurredAt, effectiveSince.get())))
                                            .replaceWith(VerificationResult.SUSPECT);
                                }
                                return Uni.createFrom().item(VerificationResult.VALID);
                            });
                });
    }

    private Uni<Optional<Instant>> compromisedEffectiveSinceAsync(
            final String actorId, final String keyRef, final Instant occurredAt) {
        return reactiveKeyRotationService.compromisedWindowsAsync(actorId, keyRef)
                .map(windows -> windows.stream()
                        .filter(w -> !occurredAt.isBefore(w.effectiveSince()))
                        .map(CompromisedWindow::effectiveSince)
                        .min(Instant::compareTo));
    }

    private VerificationResult verifyCryptographic(final LedgerEntry entry) {
        if (entry.agentPublicKey == null) {
            LOG.warnf("Entry %s has agentSignature but no agentPublicKey — record is corrupt", entry.id);
            return VerificationResult.INVALID;
        }
        try {
            final KeyFactory kf = KeyFactory.getInstance("Ed25519");
            final PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(entry.agentPublicKey));
            final Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(pub);
            sig.update(LedgerMerkleTree.canonicalBytes(entry));
            return sig.verify(entry.agentSignature) ? VerificationResult.VALID : VerificationResult.INVALID;
        } catch (final Exception e) {
            LOG.debugf("Ed25519 verify failed for entry %s (%s) — likely corrupt key data or JVM config issue",
                    entry.id, e.getMessage());
            return VerificationResult.INVALID;
        }
    }
}

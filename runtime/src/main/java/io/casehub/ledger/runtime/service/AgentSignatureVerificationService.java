package io.casehub.ledger.runtime.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.core.model.AgentSignatureSuspectEvent;
import io.casehub.ledger.core.signing.AgentCryptographicVerifier;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.core.model.CompromisedWindow;
import io.casehub.ledger.core.model.VerificationResult;

/**
 * Blocking-tier CDI bean for agent signature verification.
 *
 * <p>
 * Covers the full verification pipeline: unsigned check, algorithm-transparent
 * cryptographic verification (via {@link AgentCryptographicVerifier}), key compromise window
 * check (via {@link KeyRotationService}), and {@link AgentSignatureSuspectEvent}
 * firing. Auto-activated — no consumer configuration required.
 *
 * <p>
 * For the reactive variant see {@link ReactiveAgentSignatureVerificationService}.
 */
@ApplicationScoped
public class AgentSignatureVerificationService {

    private static final Logger log = Logger.getLogger(AgentSignatureVerificationService.class);

    @Inject
    LedgerEntryRepository ledgerRepo;

    @Inject
    KeyRotationService keyRotationService;

    @Inject
    Event<AgentSignatureSuspectEvent> suspectEvent;

    /**
     * Verifies the agent signature on the given entry.
     *
     * @param entryId   the entry to verify
     * @param tenancyId the tenant scope
     * @return {@link VerificationResult#UNSIGNED} if no signature stored;
     *         {@link VerificationResult#VALID} if the signature verifies and the key is not compromised;
     *         {@link VerificationResult#SUSPECT} if the signature verifies but the key was subsequently
     *         reported {@link io.casehub.ledger.api.model.KeyRotationReason#COMPROMISED} within the
     *         applicable time window — fires {@link AgentSignatureSuspectEvent};
     *         {@link VerificationResult#INVALID} if verification fails
     * @throws IllegalArgumentException if the entry does not exist
     */
    @Transactional
    public VerificationResult verifyAgentSignature(final UUID entryId, final String tenancyId) {
        final LedgerEntry entry = ledgerRepo.findEntryById(entryId, tenancyId)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found: " + entryId));

        if (entry.agentSignature == null) {
            return VerificationResult.UNSIGNED;
        }

        final VerificationResult cryptoResult = AgentCryptographicVerifier.verifyCryptographic(entry);
        if (cryptoResult != VerificationResult.VALID) {
            return cryptoResult;
        }

        if (entry.agentKeyRef != null && entry.actorId != null) {
            final Optional<Instant> effectiveSince =
                    compromisedEffectiveSince(entry.actorId, entry.agentKeyRef, entry.occurredAt);
            if (effectiveSince.isPresent()) {
                final AgentSignatureSuspectEvent payload = new AgentSignatureSuspectEvent(
                        entryId, entry.actorId, entry.agentKeyRef,
                        entry.occurredAt, effectiveSince.get());
                suspectEvent.fire(payload);
                suspectEvent.fireAsync(payload)
                        .exceptionally(ex -> { log.debugf(ex, "AgentSignatureSuspectEvent async observer failed"); return null; });
                return VerificationResult.SUSPECT;
            }
        }

        return VerificationResult.VALID;
    }

    private Optional<Instant> compromisedEffectiveSince(
            final String actorId, final String keyRef, final Instant occurredAt) {
        return keyRotationService.compromisedWindows(actorId, keyRef)
                .stream()
                .filter(w -> !occurredAt.isBefore(w.effectiveSince()))
                .map(CompromisedWindow::effectiveSince)
                .min(Instant::compareTo);
    }
}

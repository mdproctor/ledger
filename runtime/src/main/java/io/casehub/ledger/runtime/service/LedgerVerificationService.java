package io.casehub.ledger.runtime.service;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.model.LedgerMerkleFrontier;
import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.model.CompromisedWindow;
import io.casehub.ledger.runtime.service.model.InclusionProof;
import io.casehub.ledger.runtime.service.model.VerificationResult;

/**
 * CDI bean exposing Merkle tree verification operations.
 * Auto-activated — no consumer configuration required.
 */
@ApplicationScoped
public class LedgerVerificationService {

    private static final Logger LOG = Logger.getLogger(LedgerVerificationService.class);

    @Inject
    LedgerEntryRepository ledgerRepo;

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    @Inject
    KeyRotationService keyRotationService;

    @Inject
    Event<AgentSignatureSuspectEvent> suspectEvent;

    /** Return the current Merkle tree root for a subject. */
    @Transactional
    public String treeRoot(final UUID subjectId) {
        final List<LedgerMerkleFrontier> frontier = em
                .createNamedQuery("LedgerMerkleFrontier.findBySubjectId", LedgerMerkleFrontier.class)
                .setParameter("subjectId", subjectId)
                .getResultList();
        if (frontier.isEmpty()) {
            throw new IllegalStateException("No entries for subject " + subjectId);
        }
        return LedgerMerkleTree.treeRoot(frontier);
    }

    /**
     * Generate an inclusion proof for the given entry.
     * Fetches all leaf hashes for the subject from the database (ordered by sequenceNumber).
     * The returned proof carries the authoritative root from the stored frontier.
     */
    @Transactional
    public InclusionProof inclusionProof(final UUID entryId) {
        final LedgerEntry entry = ledgerRepo.findEntryById(entryId).orElse(null);
        if (entry == null)
            throw new IllegalArgumentException("Entry not found: " + entryId);

        final List<LedgerEntry> allForSubject = ledgerRepo.findBySubjectId(entry.subjectId);

        final List<String> leafHashes = allForSubject.stream()
                .map(e -> e.digest)
                .toList();

        final int k = entry.sequenceNumber - 1;
        final String root = treeRoot(entry.subjectId); // authoritative root from frontier
        final InclusionProof proof = LedgerMerkleTree.inclusionProof(
                entryId, k, leafHashes.size(), leafHashes);

        return new InclusionProof(entryId, k, leafHashes.size(),
                proof.leafHash(), proof.siblings(), root);
    }

    /**
     * Verify that all stored digests are consistent with recomputed leaf hashes.
     * Returns false if any entry's stored digest doesn't match its canonical hash.
     */
    @Transactional
    public boolean verify(final UUID subjectId) {
        final List<LedgerEntry> entries = ledgerRepo.findBySubjectId(subjectId);

        List<LedgerMerkleFrontier> frontier = new ArrayList<>();
        for (final LedgerEntry entry : entries) {
            final String expected = LedgerMerkleTree.leafHash(entry);
            if (!expected.equals(entry.digest))
                return false;
            frontier = LedgerMerkleTree.append(expected, frontier, subjectId);
        }

        if (frontier.isEmpty())
            return true;

        final String computed = LedgerMerkleTree.treeRoot(frontier);
        final String stored = treeRoot(subjectId);
        return computed.equals(stored);
    }

    /**
     * Ed25519 signature verification against stored public key bytes.
     * Returns {@link VerificationResult#VALID} or {@link VerificationResult#INVALID}.
     * Does NOT check compromise windows.
     */
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

    /**
     * Returns the earliest compromise window effectiveSince that covers {@code occurredAt},
     * or empty if the entry is not in any compromise window.
     */
    private Optional<Instant> compromisedEffectiveSince(
            final String actorId, final String keyRef, final Instant occurredAt) {
        return keyRotationService.compromisedWindows(actorId, keyRef)
                .stream()
                .filter(w -> !occurredAt.isBefore(w.effectiveSince()))
                .map(CompromisedWindow::effectiveSince)
                .min(Instant::compareTo);
    }

    /**
     * Verifies the agent signature on the given entry.
     *
     * @param entryId the entry to verify
     * @return {@link VerificationResult#UNSIGNED} if no signature stored;
     *         {@link VerificationResult#VALID} if the signature verifies and the key is not compromised;
     *         {@link VerificationResult#SUSPECT} if the signature verifies but the key was subsequently
     *         reported {@link io.casehub.ledger.api.model.KeyRotationReason#COMPROMISED} within the
     *         applicable time window — fires {@link AgentSignatureSuspectEvent};
     *         {@link VerificationResult#INVALID} if verification fails
     * @throws IllegalArgumentException if the entry does not exist
     */
    @Transactional
    public VerificationResult verifyAgentSignature(final UUID entryId) {
        final LedgerEntry entry = ledgerRepo.findEntryById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found: " + entryId));

        if (entry.agentSignature == null) {
            return VerificationResult.UNSIGNED;
        }

        final VerificationResult cryptoResult = verifyCryptographic(entry);
        if (cryptoResult != VerificationResult.VALID) {
            return cryptoResult;
        }

        if (entry.agentKeyRef != null && entry.actorId != null) {
            final Optional<Instant> effectiveSince =
                    compromisedEffectiveSince(entry.actorId, entry.agentKeyRef, entry.occurredAt);
            if (effectiveSince.isPresent()) {
                suspectEvent.fire(new AgentSignatureSuspectEvent(
                        entryId, entry.actorId, entry.agentKeyRef,
                        entry.occurredAt, effectiveSince.get()));
                return VerificationResult.SUSPECT;
            }
        }

        return VerificationResult.VALID;
    }

}

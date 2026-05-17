package io.casehub.ledger.runtime.service;

import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.KeyRotationEntry;
import io.casehub.ledger.runtime.repository.KeyRotationRepository;
import io.casehub.ledger.runtime.service.model.CompromisedWindow;

/**
 * CDI bean for recording and querying signing key rotation events.
 *
 * <p>
 * Each rotation is persisted as a {@link KeyRotationEntry} — a first-class
 * immutable ledger entry in the tamper-evident chain, queryable per actor.
 */
@ApplicationScoped
public class KeyRotationService {

    @Inject
    KeyRotationRepository repository;

    /**
     * Record a signing key rotation event.
     *
     * @param actorId        the actor whose key is being rotated
     * @param previousKeyRef keyRef of the key being retired; null if unknown
     * @param newKeyRef      keyRef of the replacement key; null for pure revocation
     * @param reason         SCHEDULED or COMPROMISED
     * @param effectiveSince for COMPROMISED: entries signed on or after this time are SUSPECT
     * @return the persisted {@link KeyRotationEntry}
     */
    @Transactional
    public KeyRotationEntry recordRotation(
            final String actorId,
            final String previousKeyRef,
            final String newKeyRef,
            final KeyRotationReason reason,
            final Instant effectiveSince) {

        final KeyRotationEntry entry = new KeyRotationEntry();
        entry.actorId = actorId;
        entry.actorRole = "KeyManager";
        entry.entryType = LedgerEntryType.COMMAND;
        entry.previousKeyRef = previousKeyRef;
        entry.newKeyRef = newKeyRef;
        entry.reason = reason;
        entry.effectiveSince = effectiveSince;
        return repository.save(entry);
    }

    /** All rotation events for an actor, ordered by {@code occurredAt} ascending. */
    @Transactional
    public List<KeyRotationEntry> rotationHistory(final String actorId) {
        return repository.findByActorId(actorId);
    }

    /**
     * Compromise windows for a specific actor and keyRef.
     * Used by {@link LedgerVerificationService} to detect SUSPECT signatures.
     */
    @Transactional
    public List<CompromisedWindow> compromisedWindows(
            final String actorId, final String keyRef) {
        return repository.findCompromisedByActorIdAndKeyRef(actorId, keyRef)
                .stream()
                .map(e -> new CompromisedWindow(e.previousKeyRef, e.effectiveSince))
                .toList();
    }
}

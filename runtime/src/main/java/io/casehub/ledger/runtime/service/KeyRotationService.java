package io.casehub.ledger.runtime.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.KeyRotationEntry;
import io.casehub.ledger.runtime.repository.KeyRotationRepository;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.model.CompromisedWindow;

/**
 * CDI bean for recording and querying signing key rotation events.
 *
 * <p>
 * Each rotation is persisted as a {@link KeyRotationEntry} via {@link LedgerEntryRepository#save},
 * ensuring Merkle chain inclusion, pseudonymisation, and enricher pipeline execution.
 * After persisting, fires {@link AgentKeyRotatedEvent} so observers can invalidate their caches.
 */
@ApplicationScoped
public class KeyRotationService {

    @Inject
    KeyRotationRepository repository;

    @Inject
    LedgerEntryRepository ledgerRepo;

    @Inject
    Event<AgentKeyRotatedEvent> keyRotatedEvent;

    /**
     * Record a signing key rotation event.
     *
     * @param actorId        the actor whose key is being rotated
     * @param previousKeyRef keyRef of the key being retired; null if unknown
     * @param newKeyRef      keyRef of the replacement key; null for pure revocation
     * @param reason         SCHEDULED or COMPROMISED
     * @param effectiveSince for COMPROMISED: entries signed on or after this time are SUSPECT
     * @param tenancyId      the tenant scope
     * @return the persisted {@link KeyRotationEntry}
     */
    @Transactional
    public KeyRotationEntry recordRotation(
            final String actorId,
            final String previousKeyRef,
            final String newKeyRef,
            final KeyRotationReason reason,
            final Instant effectiveSince,
            final String tenancyId) {

        final KeyRotationEntry entry = new KeyRotationEntry();
        entry.actorId = actorId;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "KeyManager";
        entry.entryType = LedgerEntryType.COMMAND;
        entry.subjectId = UUID.nameUUIDFromBytes(
                actorId.getBytes(StandardCharsets.UTF_8));
        entry.previousKeyRef = previousKeyRef;
        entry.newKeyRef = newKeyRef;
        entry.reason = reason;
        entry.effectiveSince = effectiveSince;
        final KeyRotationEntry persisted = (KeyRotationEntry) ledgerRepo.save(entry, tenancyId);
        keyRotatedEvent.fire(new AgentKeyRotatedEvent(actorId, previousKeyRef, newKeyRef));
        return persisted;
    }

    /** All rotation events for an actor within the given tenant, ordered by {@code occurredAt} ascending. */
    public List<KeyRotationEntry> rotationHistory(final String actorId, final String tenancyId) {
        return repository.findByActorId(actorId, tenancyId);
    }

    /**
     * Compromise windows for a specific actor and keyRef.
     * Used by {@link AgentSignatureVerificationService} to detect SUSPECT signatures.
     */
    public List<CompromisedWindow> compromisedWindows(
            final String actorId, final String keyRef) {
        return repository.findCompromisedByActorIdAndKeyRef(actorId, keyRef)
                .stream()
                .map(e -> new CompromisedWindow(e.previousKeyRef, e.effectiveSince))
                .toList();
    }
}

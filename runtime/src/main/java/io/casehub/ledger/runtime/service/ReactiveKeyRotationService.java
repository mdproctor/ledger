package io.casehub.ledger.runtime.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import io.smallrye.mutiny.Uni;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.KeyRotationEntry;
import io.casehub.ledger.runtime.repository.ReactiveLedgerEntryRepository;
import io.casehub.ledger.runtime.repository.ReactiveKeyRotationRepository;
import io.casehub.ledger.runtime.service.model.CompromisedWindow;

/**
 * Reactive-tier CDI bean for signing key rotation operations.
 *
 * <p>
 * Present only when {@code casehub.ledger.reactive.enabled=true} — excluded by
 * {@code LedgerProcessor} otherwise. Consumers that run on a JDBC-only datasource
 * must not depend on this bean.
 *
 * <p>
 * Fires {@link AgentKeyRotatedEvent} via {@code fireAsync()} after the Uni persist completes.
 * Fire-and-forget: observer failure is invisible to the Uni pipeline — benign for cache eviction.
 */
@ApplicationScoped
public class ReactiveKeyRotationService {

    @Inject
    ReactiveKeyRotationRepository reactiveRepository;

    /** Used only by {@link #recordRotationAsync} — saves go through the ledger pipeline. */
    @Inject
    ReactiveLedgerEntryRepository reactiveLedgerRepo;

    @Inject
    Event<AgentKeyRotatedEvent> keyRotatedEvent;

    /**
     * Compromise windows for a specific actor and keyRef.
     * Used by {@link ReactiveAgentSignatureVerificationService} to detect SUSPECT signatures.
     */
    public Uni<List<CompromisedWindow>> compromisedWindowsAsync(
            final String actorId, final String keyRef) {
        return reactiveRepository.findCompromisedByActorIdAndKeyRef(actorId, keyRef)
                .map(entries -> entries.stream()
                        .map(e -> new CompromisedWindow(e.previousKeyRef, e.effectiveSince))
                        .toList());
    }

    /** All rotation events for an actor, ordered by {@code occurredAt} ascending. */
    public Uni<List<KeyRotationEntry>> rotationHistoryAsync(final String actorId) {
        return reactiveRepository.findByActorId(actorId);
    }

    /**
     * Record a signing key rotation event reactively.
     *
     * @param actorId        the actor whose key is being rotated
     * @param previousKeyRef keyRef of the key being retired; null if unknown
     * @param newKeyRef      keyRef of the replacement key; null for pure revocation
     * @param reason         SCHEDULED or COMPROMISED
     * @param effectiveSince for COMPROMISED: entries signed on or after this time are SUSPECT
     * @return a {@link Uni} completing with the persisted {@link KeyRotationEntry}
     */
    public Uni<KeyRotationEntry> recordRotationAsync(
            final String actorId,
            final String previousKeyRef,
            final String newKeyRef,
            final KeyRotationReason reason,
            final Instant effectiveSince) {

        Objects.requireNonNull(actorId, "actorId");
        final KeyRotationEntry entry = new KeyRotationEntry();
        entry.actorId = actorId;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "KeyManager";
        entry.entryType = LedgerEntryType.COMMAND;
        entry.subjectId = UUID.nameUUIDFromBytes(actorId.getBytes(StandardCharsets.UTF_8));
        entry.previousKeyRef = previousKeyRef;
        entry.newKeyRef = newKeyRef;
        entry.reason = reason;
        entry.effectiveSince = effectiveSince;
        return reactiveLedgerRepo.save(entry)
                .map(e -> (KeyRotationEntry) e)
                // Fire-and-forget: observer failure is invisible; benign for cache eviction
                .invoke(e -> keyRotatedEvent.fireAsync(
                        new AgentKeyRotatedEvent(actorId, previousKeyRef, newKeyRef)));
    }
}

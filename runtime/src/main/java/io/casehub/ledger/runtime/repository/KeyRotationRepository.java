package io.casehub.ledger.runtime.repository;

import java.util.List;

import io.casehub.ledger.runtime.model.KeyRotationEntry;

/**
 * SPI for persisting and querying {@link KeyRotationEntry} records.
 */
public interface KeyRotationRepository {

    /**
     * Persist a key rotation entry.
     *
     * @param entry the entry to persist; must not be null
     * @return the persisted entry (post-{@code @PrePersist})
     */
    KeyRotationEntry save(KeyRotationEntry entry);

    /**
     * All rotation events for an actor, ordered by {@code occurredAt} ascending.
     */
    List<KeyRotationEntry> findByActorId(String actorId);

    /**
     * All {@code COMPROMISED} rotation events for a specific actor and keyRef,
     * ordered by {@code effectiveSince} ascending.
     */
    List<KeyRotationEntry> findCompromisedByActorIdAndKeyRef(String actorId, String keyRef);
}

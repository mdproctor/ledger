package io.casehub.ledger.runtime.repository;

import java.util.List;

import io.casehub.ledger.runtime.model.KeyRotationEntry;

/**
 * SPI for querying {@link KeyRotationEntry} records.
 * Rotation entries are persisted via {@link LedgerEntryRepository#save(io.casehub.ledger.runtime.model.LedgerEntry)},
 * which ensures Merkle chain inclusion, pseudonymisation, and enricher pipeline execution.
 */
public interface KeyRotationRepository {

    /** All rotation events for an actor, ordered by {@code occurredAt} ascending. */
    List<KeyRotationEntry> findByActorId(String actorId);

    /** All COMPROMISED rotation events for a specific actor and keyRef, ordered by effectiveSince ascending. */
    List<KeyRotationEntry> findCompromisedByActorIdAndKeyRef(String actorId, String keyRef);
}

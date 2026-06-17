package io.casehub.ledger.runtime.repository;

import java.util.List;

import io.casehub.ledger.runtime.model.KeyRotationEntry;

/**
 * SPI for querying {@link KeyRotationEntry} records.
 * Rotation entries are persisted via {@link LedgerEntryRepository#save(io.casehub.ledger.runtime.model.LedgerEntry)},
 * which ensures Merkle chain inclusion, pseudonymisation, and enricher pipeline execution.
 */
public interface KeyRotationRepository {

    /** All rotation events for an actor within the given tenant, ordered by {@code occurredAt} ascending. */
    List<KeyRotationEntry> findByActorId(String actorId, String tenancyId);

    /**
     * All COMPROMISED rotation events for a specific actor and keyRef, ordered by effectiveSince ascending.
     * Cross-tenant: a compromised key is a global security signal, not tenant-scoped.
     */
    List<KeyRotationEntry> findCompromisedByActorIdAndKeyRef(String actorId, String keyRef);
}

package io.casehub.ledger.runtime.repository;

import java.util.List;

import io.casehub.ledger.runtime.model.KeyRotationEntry;
import io.smallrye.mutiny.Uni;

/**
 * Reactive SPI for querying {@link KeyRotationEntry} records.
 *
 * <p>
 * Mirrors {@link KeyRotationRepository} with all return types wrapped in {@link Uni}.
 * Rotation entries are persisted via
 * {@link io.casehub.ledger.runtime.repository.ReactiveLedgerEntryRepository#save},
 * which ensures Merkle chain inclusion, pseudonymisation, and enricher pipeline execution.
 *
 * <p>
 * No production implementation is bundled in {@code casehub-ledger} — consumers provide
 * their own implementation using their reactive persistence stack (e.g. Hibernate Reactive,
 * reactive MongoDB). The test suite uses {@code BlockingReactiveKeyRotationRepository} as
 * a blocking shim over the JDBC-based {@link io.casehub.ledger.runtime.repository.jpa.JpaKeyRotationRepository}.
 */
public interface ReactiveKeyRotationRepository {

    /** All rotation events for an actor, ordered by {@code occurredAt} ascending. */
    Uni<List<KeyRotationEntry>> findByActorId(String actorId);

    /** All COMPROMISED rotation events for a specific actor and keyRef, ordered by effectiveSince ascending. */
    Uni<List<KeyRotationEntry>> findCompromisedByActorIdAndKeyRef(String actorId, String keyRef);
}

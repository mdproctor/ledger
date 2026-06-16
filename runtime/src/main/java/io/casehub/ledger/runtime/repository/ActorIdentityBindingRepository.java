package io.casehub.ledger.runtime.repository;

import io.casehub.ledger.runtime.model.ActorIdentityBindingEntry;
import java.util.List;
import java.util.Optional;

/**
 * SPI for querying {@link ActorIdentityBindingEntry} records.
 *
 * <p>Binding entries are persisted via {@link io.casehub.ledger.runtime.repository.LedgerEntryRepository#save},
 * which ensures Merkle chain inclusion, sequence allocation, enricher pipeline execution,
 * and agent signing. {@code ActorIdentityBindingObserver} is the sole writer.
 *
 * <p>Implementations must filter results by both {@code actorId} and {@code tenancyId} —
 * two tenants can share the same {@code actorId} (e.g. a shared LLM persona like
 * {@code claude:reviewer@v1}); returning cross-tenant results is a data isolation violation.
 */
public interface ActorIdentityBindingRepository {

    /**
     * Return the most recent binding entry for the given actor and tenant,
     * ordered by {@code sequenceNumber} descending.
     */
    Optional<ActorIdentityBindingEntry> latestBindingFor(String actorId, String tenancyId);

    /**
     * Return all binding entries for the given actor and tenant,
     * ordered by {@code sequenceNumber} ascending.
     */
    List<ActorIdentityBindingEntry> bindingHistoryFor(String actorId, String tenancyId);
}

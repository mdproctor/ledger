package io.casehub.ledger.runtime.repository;

import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-tenant read operations for trust computation, health checks, retention,
 * and compliance export. Produced via {@code @CrossTenant} CDI qualifier — only
 * injectable by system-level beans with cross-tenant authority.
 */
public interface CrossTenantLedgerEntryRepository {

    /**
     * Return all ledger entries across all subjects (for retention and bulk operations).
     *
     * @return list of all entries; empty if none exist
     */
    List<LedgerEntry> listAll();

    /**
     * Return all EVENT-type ledger entries across all subjects (for trust score computation).
     *
     * @return list of all EVENT entries; empty if none exist
     */
    List<LedgerEntry> findAllEvents();

    /**
     * Return all EVENT-type ledger entries for the given actor.
     *
     * <p>
     * Used by incremental trust score recomputation to fetch all EVENT entries
     * for a single actor when their trust score needs to be recomputed. Filters
     * by {@code entryType = EVENT} and {@code actorId}.
     *
     * @param actorId the actor identity to filter by
     * @return list of EVENT entries for the actor; empty if none exist
     */
    List<LedgerEntry> findEventsByActorId(String actorId);

    /**
     * Return all ledger entries whose {@code occurredAt} falls within
     * [{@code from}, {@code to}] inclusive, ordered by {@code occurredAt} ascending.
     *
     * <p>
     * Use for bulk audit exports and retention window queries.
     *
     * @param from start of the time range (inclusive)
     * @param to end of the time range (inclusive)
     * @return ordered list of entries; empty if none match
     */
    List<LedgerEntry> findByTimeRange(Instant from, Instant to);

    /**
     * Return all attestations for the given set of ledger entry IDs, grouped by entry ID.
     *
     * @param entryIds the set of ledger entry UUIDs
     * @return map from entry ID to its attestations; empty map if {@code entryIds} is empty
     */
    Map<UUID, List<LedgerAttestation>> findAttestationsForEntries(Set<UUID> entryIds);
}

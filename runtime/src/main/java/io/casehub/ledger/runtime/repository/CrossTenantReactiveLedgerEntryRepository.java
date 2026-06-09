package io.casehub.ledger.runtime.repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.smallrye.mutiny.Uni;

/**
 * Reactive cross-tenant read operations. Build-gated via
 * {@code casehub.ledger.reactive.enabled=true}. Produced via {@code @CrossTenant}.
 *
 * <p>
 * All methods query across tenant boundaries. These operations are intended for
 * infrastructure use cases (reporting, global dashboards, analytics pipelines)
 * where cross-tenant aggregation is explicitly required. Application logic that
 * operates within a single tenant scope should use {@link ReactiveLedgerEntryRepository}.
 *
 * <p>
 * Access control is the caller's responsibility — this interface does not enforce
 * tenant isolation. Consumers must gate calls to these methods with appropriate
 * authorization checks before use.
 */
public interface CrossTenantReactiveLedgerEntryRepository {

    Uni<List<LedgerEntry>> listAll();

    Uni<List<LedgerEntry>> findAllEvents();

    /**
     * Return all EVENT-type ledger entries for the given actor across all tenants.
     *
     * <p>
     * Used by incremental trust score recomputation when actors operate across
     * tenant boundaries.
     *
     * @param actorId the actor identity to filter by
     * @return list of EVENT entries for the actor; empty if none exist
     */
    Uni<List<LedgerEntry>> findEventsByActorId(String actorId);

    Uni<List<LedgerEntry>> findByTimeRange(Instant from, Instant to);

    Uni<Map<UUID, List<LedgerAttestation>>> findAttestationsForEntries(Set<UUID> entryIds);

    Uni<Map<UUID, List<LedgerAttestation>>> findAttestationsByActorId(String actorId);
}

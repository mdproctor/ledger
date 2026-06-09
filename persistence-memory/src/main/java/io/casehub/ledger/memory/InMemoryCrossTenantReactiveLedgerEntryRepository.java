package io.casehub.ledger.memory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.CrossTenantReactiveLedgerEntryRepository;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;

/**
 * Reactive in-memory implementation of {@link CrossTenantReactiveLedgerEntryRepository}.
 *
 * <p>Delegates to the blocking {@link InMemoryCrossTenantLedgerEntryRepository} wrapped
 * in {@link Uni#createFrom()}.
 */
@Alternative
@Priority(1)
@ApplicationScoped
@IfBuildProperty(name = "casehub.ledger.reactive.enabled", stringValue = "true")
public class InMemoryCrossTenantReactiveLedgerEntryRepository implements CrossTenantReactiveLedgerEntryRepository {

    @Inject
    InMemoryCrossTenantLedgerEntryRepository blocking;

    @Override
    public Uni<List<LedgerEntry>> listAll() {
        return Uni.createFrom().item(blocking::listAll);
    }

    @Override
    public Uni<List<LedgerEntry>> findAllEvents() {
        return Uni.createFrom().item(blocking::findAllEvents);
    }

    @Override
    public Uni<List<LedgerEntry>> findEventsByActorId(final String actorId) {
        return Uni.createFrom().item(() -> blocking.findEventsByActorId(actorId));
    }

    @Override
    public Uni<List<LedgerEntry>> findByTimeRange(final Instant from, final Instant to) {
        return Uni.createFrom().item(() -> blocking.findByTimeRange(from, to));
    }

    @Override
    public Uni<Map<UUID, List<LedgerAttestation>>> findAttestationsForEntries(final Set<UUID> entryIds) {
        return Uni.createFrom().item(() -> blocking.findAttestationsForEntries(entryIds));
    }

    @Override
    public Uni<Map<UUID, List<LedgerAttestation>>> findAttestationsByActorId(final String actorId) {
        return Uni.createFrom().item(() -> blocking.findAttestationsByActorId(actorId));
    }
}

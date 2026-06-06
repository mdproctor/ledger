package io.casehub.ledger.memory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.ReactiveLedgerEntryRepository;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;

@Alternative
@Priority(1)
@ApplicationScoped
@IfBuildProperty(name = "casehub.ledger.reactive.enabled", stringValue = "true")
public class InMemoryReactiveLedgerEntryRepository implements ReactiveLedgerEntryRepository {

    @Inject
    InMemoryLedgerEntryRepository blocking;

    @Override
    public Uni<LedgerEntry> save(final LedgerEntry entry) {
        return Uni.createFrom().item(() -> blocking.save(entry));
    }

    @Override
    public Uni<List<LedgerEntry>> listAll() {
        return Uni.createFrom().item(blocking::listAll);
    }

    @Override
    public Uni<List<LedgerEntry>> findBySubjectId(final UUID subjectId) {
        return Uni.createFrom().item(() -> blocking.findBySubjectId(subjectId));
    }

    @Override
    public Uni<List<LedgerEntry>> findBySubjectIdAndTimeRange(final UUID subjectId,
            final Instant from, final Instant to) {
        return Uni.createFrom().item(() -> blocking.findBySubjectIdAndTimeRange(subjectId, from, to));
    }

    @Override
    public Uni<Optional<LedgerEntry>> findLatestBySubjectId(final UUID subjectId) {
        return Uni.createFrom().item(() -> blocking.findLatestBySubjectId(subjectId));
    }

    @Override
    public Uni<Optional<LedgerEntry>> findEntryById(final UUID id) {
        return Uni.createFrom().item(() -> blocking.findEntryById(id));
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
    public Uni<List<LedgerEntry>> findByActorId(final String actorId,
            final Instant from, final Instant to) {
        return Uni.createFrom().item(() -> blocking.findByActorId(actorId, from, to));
    }

    @Override
    public Uni<List<LedgerEntry>> findByActorRole(final String actorRole,
            final Instant from, final Instant to) {
        return Uni.createFrom().item(() -> blocking.findByActorRole(actorRole, from, to));
    }

    @Override
    public Uni<List<LedgerEntry>> findByTimeRange(final Instant from, final Instant to) {
        return Uni.createFrom().item(() -> blocking.findByTimeRange(from, to));
    }

    @Override
    public Uni<List<LedgerEntry>> findCausedBy(final UUID entryId) {
        return Uni.createFrom().item(() -> blocking.findCausedBy(entryId));
    }

    @Override
    public Uni<LedgerAttestation> saveAttestation(final LedgerAttestation attestation) {
        return Uni.createFrom().item(() -> blocking.saveAttestation(attestation));
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByEntryId(final UUID ledgerEntryId) {
        return Uni.createFrom().item(() -> blocking.findAttestationsByEntryId(ledgerEntryId));
    }

    @Override
    public Uni<Map<UUID, List<LedgerAttestation>>> findAttestationsForEntries(
            final Set<UUID> entryIds) {
        return Uni.createFrom().item(() -> blocking.findAttestationsForEntries(entryIds));
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByEntryIdAndCapabilityTag(
            final UUID entryId, final String capabilityTag) {
        return Uni.createFrom().item(
                () -> blocking.findAttestationsByEntryIdAndCapabilityTag(entryId, capabilityTag));
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByEntryIdGlobal(final UUID entryId) {
        return Uni.createFrom().item(() -> blocking.findAttestationsByEntryIdGlobal(entryId));
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByAttestorIdAndCapabilityTag(
            final String attestorId, final String capabilityTag) {
        return Uni.createFrom().item(
                () -> blocking.findAttestationsByAttestorIdAndCapabilityTag(attestorId, capabilityTag));
    }
}

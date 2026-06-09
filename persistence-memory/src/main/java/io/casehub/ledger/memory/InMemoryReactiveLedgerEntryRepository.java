package io.casehub.ledger.memory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    public Uni<LedgerEntry> save(final LedgerEntry entry, final String tenancyId) {
        return Uni.createFrom().item(() -> blocking.save(entry, tenancyId));
    }

    @Override
    public Uni<List<LedgerEntry>> findBySubjectId(final UUID subjectId, final String tenancyId) {
        return Uni.createFrom().item(() -> blocking.findBySubjectId(subjectId, tenancyId));
    }

    @Override
    public Uni<List<LedgerEntry>> findBySubjectIdAndTimeRange(final UUID subjectId,
            final Instant from, final Instant to, final String tenancyId) {
        return Uni.createFrom().item(() -> blocking.findBySubjectIdAndTimeRange(subjectId, from, to, tenancyId));
    }

    @Override
    public Uni<Optional<LedgerEntry>> findLatestBySubjectId(final UUID subjectId, final String tenancyId) {
        return Uni.createFrom().item(() -> blocking.findLatestBySubjectId(subjectId, tenancyId));
    }

    @Override
    public Uni<Optional<LedgerEntry>> findEntryById(final UUID id, final String tenancyId) {
        return Uni.createFrom().item(() -> blocking.findEntryById(id, tenancyId));
    }

    @Override
    public Uni<List<LedgerEntry>> findByActorId(final String actorId,
            final Instant from, final Instant to, final String tenancyId) {
        return Uni.createFrom().item(() -> blocking.findByActorId(actorId, from, to, tenancyId));
    }

    @Override
    public Uni<List<LedgerEntry>> findByActorRole(final String actorRole,
            final Instant from, final Instant to, final String tenancyId) {
        return Uni.createFrom().item(() -> blocking.findByActorRole(actorRole, from, to, tenancyId));
    }

    @Override
    public Uni<List<LedgerEntry>> findCausedBy(final UUID entryId, final String tenancyId) {
        return Uni.createFrom().item(() -> blocking.findCausedBy(entryId, tenancyId));
    }

    @Override
    public Uni<LedgerAttestation> saveAttestation(final LedgerAttestation attestation, final String tenancyId) {
        return Uni.createFrom().item(() -> blocking.saveAttestation(attestation, tenancyId));
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByEntryId(final UUID ledgerEntryId, final String tenancyId) {
        return Uni.createFrom().item(() -> blocking.findAttestationsByEntryId(ledgerEntryId, tenancyId));
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByEntryIdAndCapabilityTag(
            final UUID entryId, final String capabilityTag, final String tenancyId) {
        return Uni.createFrom().item(
                () -> blocking.findAttestationsByEntryIdAndCapabilityTag(entryId, capabilityTag, tenancyId));
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByEntryIdGlobal(final UUID entryId, final String tenancyId) {
        return Uni.createFrom().item(() -> blocking.findAttestationsByEntryIdGlobal(entryId, tenancyId));
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByAttestorIdAndCapabilityTag(
            final String attestorId, final String capabilityTag, final String tenancyId) {
        return Uni.createFrom().item(
                () -> blocking.findAttestationsByAttestorIdAndCapabilityTag(attestorId, capabilityTag, tenancyId));
    }
}

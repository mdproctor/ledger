package io.casehub.ledger.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.casehub.ledger.runtime.qualifier.CrossTenant;
import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.test.junit.QuarkusTest;
import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;

/**
 * Integration tests for {@link InMemoryLedgerEntryRepository}.
 *
 * <p>
 * Field access on {@link LedgerEntry} instances must go through
 * {@link MemoryTestEntry} accessor methods — Quarkus bytecode enhancement
 * changes {@code @Entity} public fields to {@code protected} in the augmented
 * classloader, so direct field reads from the test class cause
 * {@link IllegalAccessError}. Reads are done via cast + accessor methods;
 * writes are done inside {@link MemoryTestEntry} factory/mutator methods.
 *
 * <p>
 * Attestation field reads use {@link LedgerAttestationAccessor}.
 */
@QuarkusTest
class InMemoryLedgerEntryRepositoryTest {

    @Inject
    InMemoryLedgerEntryRepository repo;

    @Inject
    @CrossTenant
    InMemoryCrossTenantLedgerEntryRepository crossTenantRepo;

    @BeforeEach
    void setUp() {
        repo.clear();
    }

    // ── save ─────────────────────────────────────────────────────────────────

    @Test
    void save_assignsId() {
        MemoryTestEntry e = entry(UUID.randomUUID(), LedgerEntryType.EVENT).withNullId();
        repo.save(e, DEFAULT_TENANT_ID);
        assertThat(e.getId()).isNotNull();
    }

    @Test
    void save_assignsOccurredAt() {
        MemoryTestEntry e = entry(UUID.randomUUID(), LedgerEntryType.EVENT).withNullOccurredAt();
        repo.save(e, DEFAULT_TENANT_ID);
        assertThat(e.getOccurredAt()).isNotNull();
    }

    @Test
    void save_assignsSequenceNumber() {
        UUID subjectId = UUID.randomUUID();
        MemoryTestEntry e1 = entry(subjectId, LedgerEntryType.EVENT);
        MemoryTestEntry e2 = entry(subjectId, LedgerEntryType.EVENT);
        repo.save(e1, DEFAULT_TENANT_ID);
        repo.save(e2, DEFAULT_TENANT_ID);
        assertThat(e1.getSequenceNumber()).isEqualTo(1);
        assertThat(e2.getSequenceNumber()).isEqualTo(2);
    }

    @Test
    void save_sequenceNumberIsPerSubject() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        MemoryTestEntry a = entry(s1, LedgerEntryType.EVENT);
        MemoryTestEntry b = entry(s2, LedgerEntryType.EVENT);
        MemoryTestEntry c = entry(s1, LedgerEntryType.EVENT);
        repo.save(a, DEFAULT_TENANT_ID);
        repo.save(b, DEFAULT_TENANT_ID);
        repo.save(c, DEFAULT_TENANT_ID);
        assertThat(a.getSequenceNumber()).isEqualTo(1);
        assertThat(b.getSequenceNumber()).isEqualTo(1);
        assertThat(c.getSequenceNumber()).isEqualTo(2);
    }

    // ── findBySubjectId ───────────────────────────────────────────────────────

    @Test
    void findBySubjectId_returnsOrderedBySequenceNumber() {
        UUID subjectId = UUID.randomUUID();
        repo.save(entry(subjectId, LedgerEntryType.EVENT), DEFAULT_TENANT_ID);
        repo.save(entry(subjectId, LedgerEntryType.COMMAND), DEFAULT_TENANT_ID);
        repo.save(entry(subjectId, LedgerEntryType.EVENT), DEFAULT_TENANT_ID);

        List<LedgerEntry> results = repo.findBySubjectId(subjectId, DEFAULT_TENANT_ID);
        assertThat(results).hasSize(3);
        assertThat(seqNum(results.get(0))).isEqualTo(1);
        assertThat(seqNum(results.get(1))).isEqualTo(2);
        assertThat(seqNum(results.get(2))).isEqualTo(3);
    }

    @Test
    void findBySubjectId_returnsEmptyForUnknownSubject() {
        assertThat(repo.findBySubjectId(UUID.randomUUID(), DEFAULT_TENANT_ID)).isEmpty();
    }

    // ── findEntryById ─────────────────────────────────────────────────────────

    @Test
    void findEntryById_returnsEntry() {
        MemoryTestEntry e = entry(UUID.randomUUID(), LedgerEntryType.EVENT);
        repo.save(e, DEFAULT_TENANT_ID);
        Optional<LedgerEntry> result = repo.findEntryById(e.getId(), DEFAULT_TENANT_ID);
        assertThat(result).isPresent();
        assertThat(id(result.get())).isEqualTo(e.getId());
    }

    @Test
    void findEntryById_returnsEmptyForUnknown() {
        assertThat(repo.findEntryById(UUID.randomUUID(), DEFAULT_TENANT_ID)).isEmpty();
    }

    // ── findLatestBySubjectId ─────────────────────────────────────────────────

    @Test
    void findLatestBySubjectId_returnsHighestSequenceNumber() {
        UUID subjectId = UUID.randomUUID();
        repo.save(entry(subjectId, LedgerEntryType.EVENT), DEFAULT_TENANT_ID);
        MemoryTestEntry last = entry(subjectId, LedgerEntryType.COMMAND);
        repo.save(last, DEFAULT_TENANT_ID);

        Optional<LedgerEntry> result = repo.findLatestBySubjectId(subjectId, DEFAULT_TENANT_ID);
        assertThat(result).isPresent();
        assertThat(id(result.get())).isEqualTo(last.getId());
    }

    // ── time range queries ────────────────────────────────────────────────────

    @Test
    void findBySubjectIdAndTimeRange_filtersInclusively() {
        UUID subjectId = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-06-01T00:00:00Z");
        Instant t3 = Instant.parse("2026-12-01T00:00:00Z");

        repo.save(entry(subjectId, LedgerEntryType.EVENT).withOccurredAt(t1), DEFAULT_TENANT_ID);
        repo.save(entry(subjectId, LedgerEntryType.EVENT).withOccurredAt(t2), DEFAULT_TENANT_ID);
        repo.save(entry(subjectId, LedgerEntryType.EVENT).withOccurredAt(t3), DEFAULT_TENANT_ID);

        List<LedgerEntry> results = repo.findBySubjectIdAndTimeRange(subjectId, t1, t2, DEFAULT_TENANT_ID);
        assertThat(results).hasSize(2);
    }

    @Test
    void findByTimeRange_acrossAllSubjects() {
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-06-01T00:00:00Z");

        repo.save(entry(UUID.randomUUID(), LedgerEntryType.EVENT).withOccurredAt(t1), DEFAULT_TENANT_ID);
        repo.save(entry(UUID.randomUUID(), LedgerEntryType.EVENT).withOccurredAt(t2), DEFAULT_TENANT_ID);
        repo.save(entry(UUID.randomUUID(), LedgerEntryType.EVENT)
                .withOccurredAt(Instant.parse("2026-12-01T00:00:00Z")), DEFAULT_TENANT_ID);

        List<LedgerEntry> results = crossTenantRepo.findByTimeRange(t1, t2);
        assertThat(results).hasSize(2);
    }

    // ── actor queries ─────────────────────────────────────────────────────────

    @Test
    void findByActorId_filtersAndOrdersByOccurredAt() {
        String actor = "actor-" + UUID.randomUUID();
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-06-01T00:00:00Z");

        repo.save(entry(UUID.randomUUID(), LedgerEntryType.EVENT)
                .withActorId(actor).withOccurredAt(t2), DEFAULT_TENANT_ID);
        repo.save(entry(UUID.randomUUID(), LedgerEntryType.EVENT)
                .withActorId(actor).withOccurredAt(t1), DEFAULT_TENANT_ID);
        repo.save(entry(UUID.randomUUID(), LedgerEntryType.EVENT).withActorId("other"), DEFAULT_TENANT_ID);

        List<LedgerEntry> results = repo.findByActorId(
                actor, Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"), DEFAULT_TENANT_ID);
        assertThat(results).hasSize(2);
        assertThat(occurredAt(results.get(0))).isEqualTo(t1);
    }

    @Test
    void findByActorRole_filters() {
        String role = "Classifier-" + UUID.randomUUID();
        repo.save(entry(UUID.randomUUID(), LedgerEntryType.EVENT).withActorRole(role), DEFAULT_TENANT_ID);

        List<LedgerEntry> results = repo.findByActorRole(
                role, Instant.EPOCH, Instant.parse("2099-01-01T00:00:00Z"), DEFAULT_TENANT_ID);
        assertThat(results).hasSize(1);
    }

    // ── causal + type queries ─────────────────────────────────────────────────

    @Test
    void findCausedBy_returnsDownstreamEntries() {
        MemoryTestEntry cause = entry(UUID.randomUUID(), LedgerEntryType.COMMAND);
        repo.save(cause, DEFAULT_TENANT_ID);
        MemoryTestEntry effect = entry(UUID.randomUUID(), LedgerEntryType.EVENT)
                .withCausedBy(cause.getId());
        repo.save(effect, DEFAULT_TENANT_ID);

        List<LedgerEntry> results = repo.findCausedBy(cause.getId(), DEFAULT_TENANT_ID);
        assertThat(results).hasSize(1);
        assertThat(id(results.get(0))).isEqualTo(effect.getId());
    }

    @Test
    void findAllEvents_returnsOnlyEvents() {
        repo.save(entry(UUID.randomUUID(), LedgerEntryType.EVENT), DEFAULT_TENANT_ID);
        repo.save(entry(UUID.randomUUID(), LedgerEntryType.COMMAND), DEFAULT_TENANT_ID);
        repo.save(entry(UUID.randomUUID(), LedgerEntryType.EVENT), DEFAULT_TENANT_ID);

        List<LedgerEntry> results = crossTenantRepo.findAllEvents();
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(e -> entryType(e) == LedgerEntryType.EVENT);
    }

    @Test
    void listAll_returnsAllEntries() {
        repo.save(entry(UUID.randomUUID(), LedgerEntryType.EVENT), DEFAULT_TENANT_ID);
        repo.save(entry(UUID.randomUUID(), LedgerEntryType.COMMAND), DEFAULT_TENANT_ID);
        assertThat(crossTenantRepo.listAll()).hasSize(2);
    }

    // ── attestations ──────────────────────────────────────────────────────────

    @Test
    void saveAttestation_andFindByEntryId() {
        MemoryTestEntry e = entry(UUID.randomUUID(), LedgerEntryType.EVENT);
        repo.save(e, DEFAULT_TENANT_ID);

        LedgerAttestation a = attestation(e.getId(), e.getSubjectId(), "attesting-actor", "*");
        repo.saveAttestation(a, DEFAULT_TENANT_ID);

        List<LedgerAttestation> results = repo.findAttestationsByEntryId(e.getId(), DEFAULT_TENANT_ID);
        assertThat(results).hasSize(1);
        assertThat(LedgerAttestationAccessor.attestorId(results.get(0))).isEqualTo("attesting-actor");
    }

    @Test
    void findAttestationsForEntries_groupsByEntryId() {
        MemoryTestEntry e1 = entry(UUID.randomUUID(), LedgerEntryType.EVENT);
        MemoryTestEntry e2 = entry(UUID.randomUUID(), LedgerEntryType.EVENT);
        repo.save(e1, DEFAULT_TENANT_ID); repo.save(e2, DEFAULT_TENANT_ID);
        repo.saveAttestation(attestation(e1.getId(), e1.getSubjectId(), "a1", "*"), DEFAULT_TENANT_ID);
        repo.saveAttestation(attestation(e1.getId(), e1.getSubjectId(), "a2", "*"), DEFAULT_TENANT_ID);
        repo.saveAttestation(attestation(e2.getId(), e2.getSubjectId(), "a3", "*"), DEFAULT_TENANT_ID);

        Map<UUID, List<LedgerAttestation>> grouped =
                crossTenantRepo.findAttestationsForEntries(Set.of(e1.getId(), e2.getId()));
        assertThat(grouped.get(e1.getId())).hasSize(2);
        assertThat(grouped.get(e2.getId())).hasSize(1);
    }

    @Test
    void findAttestationsByEntryIdAndCapabilityTag_filters() {
        MemoryTestEntry e = entry(UUID.randomUUID(), LedgerEntryType.EVENT);
        repo.save(e, DEFAULT_TENANT_ID);
        repo.saveAttestation(attestation(e.getId(), e.getSubjectId(), "a1", "review"), DEFAULT_TENANT_ID);
        repo.saveAttestation(attestation(e.getId(), e.getSubjectId(), "a2", "*"), DEFAULT_TENANT_ID);

        List<LedgerAttestation> results =
                repo.findAttestationsByEntryIdAndCapabilityTag(e.getId(), "review", DEFAULT_TENANT_ID);
        assertThat(results).hasSize(1);
        assertThat(LedgerAttestationAccessor.attestorId(results.get(0))).isEqualTo("a1");
    }

    @Test
    void findAttestationsByEntryIdGlobal_returnsOnlyStar() {
        MemoryTestEntry e = entry(UUID.randomUUID(), LedgerEntryType.EVENT);
        repo.save(e, DEFAULT_TENANT_ID);
        repo.saveAttestation(attestation(e.getId(), e.getSubjectId(), "a1", "*"), DEFAULT_TENANT_ID);
        repo.saveAttestation(attestation(e.getId(), e.getSubjectId(), "a2", "review"), DEFAULT_TENANT_ID);

        List<LedgerAttestation> results = repo.findAttestationsByEntryIdGlobal(e.getId(), DEFAULT_TENANT_ID);
        assertThat(results).hasSize(1);
        assertThat(LedgerAttestationAccessor.capabilityTag(results.get(0))).isEqualTo("*");
    }

    @Test
    void findAttestationsByAttestorIdAndCapabilityTag_filters() {
        MemoryTestEntry e = entry(UUID.randomUUID(), LedgerEntryType.EVENT);
        repo.save(e, DEFAULT_TENANT_ID);
        repo.saveAttestation(attestation(e.getId(), e.getSubjectId(), "claude:v1", "review"), DEFAULT_TENANT_ID);
        repo.saveAttestation(attestation(e.getId(), e.getSubjectId(), "claude:v1", "*"), DEFAULT_TENANT_ID);
        repo.saveAttestation(attestation(e.getId(), e.getSubjectId(), "other", "review"), DEFAULT_TENANT_ID);

        List<LedgerAttestation> results =
                repo.findAttestationsByAttestorIdAndCapabilityTag("claude:v1", "review", DEFAULT_TENANT_ID);
        assertThat(results).hasSize(1);
    }

    // ── session lifecycle / clear ─────────────────────────────────────────────

    @Test
    void clear_removesAllEntriesAndAttestations() {
        UUID subjectId = UUID.randomUUID();
        MemoryTestEntry e = entry(subjectId, LedgerEntryType.EVENT);
        repo.save(e, DEFAULT_TENANT_ID);
        repo.saveAttestation(attestation(e.getId(), subjectId, "actor", "*"), DEFAULT_TENANT_ID);

        repo.clear();

        assertThat(crossTenantRepo.listAll()).isEmpty();
        assertThat(repo.findAttestationsByEntryId(e.getId(), DEFAULT_TENANT_ID)).isEmpty();
    }

    @Test
    void clear_resetsSequenceCountersSoNextSessionStartsAt1() {
        UUID subjectId = UUID.randomUUID();
        MemoryTestEntry session1Entry = entry(subjectId, LedgerEntryType.EVENT);
        repo.save(session1Entry, DEFAULT_TENANT_ID);
        assertThat(session1Entry.getSequenceNumber()).isEqualTo(1);

        repo.clear();

        MemoryTestEntry session2Entry = entry(subjectId, LedgerEntryType.EVENT);
        repo.save(session2Entry, DEFAULT_TENANT_ID);
        assertThat(session2Entry.getSequenceNumber())
                .as("sequence counter resets to 1 after clear — new session starts fresh")
                .isEqualTo(1);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private MemoryTestEntry entry(UUID subjectId, LedgerEntryType type) {
        return MemoryTestEntry.of(subjectId, type);
    }

    private LedgerAttestation attestation(UUID entryId, UUID subjectId,
            String attestorId, String capabilityTag) {
        return LedgerAttestationAccessor.create(entryId, subjectId, attestorId, capabilityTag);
    }

    // ── field readers ─────────────────────────────────────────────────────────
    // Access LedgerEntry fields through the MemoryTestEntry subclass to bypass
    // the protected access restriction introduced by Quarkus bytecode enhancement.

    private static UUID id(LedgerEntry e) {
        return ((MemoryTestEntry) e).getId();
    }

    private static int seqNum(LedgerEntry e) {
        return ((MemoryTestEntry) e).getSequenceNumber();
    }

    private static LedgerEntryType entryType(LedgerEntry e) {
        return ((MemoryTestEntry) e).getEntryType();
    }

    private static Instant occurredAt(LedgerEntry e) {
        return ((MemoryTestEntry) e).getOccurredAt();
    }
}

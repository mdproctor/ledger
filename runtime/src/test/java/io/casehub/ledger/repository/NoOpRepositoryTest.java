package io.casehub.ledger.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.ActorTrustScore.ScoreType;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.repository.NoOpActorIdentityBindingRepository;
import io.casehub.ledger.runtime.repository.NoOpActorTrustScoreRepository;
import io.casehub.ledger.runtime.repository.NoOpLedgerEntryRepository;
import io.casehub.ledger.service.supplement.TestEntry;
import io.casehub.platform.api.identity.ActorType;

/**
 * Unit tests for {@link NoOpLedgerEntryRepository} and {@link NoOpActorIdentityBindingRepository}.
 *
 * <p>No Quarkus container — both no-ops have zero CDI dependencies and are instantiated
 * directly. Tests verify the CDI-satisfaction contract: all read methods return empty,
 * save methods return the argument unchanged.
 */
class NoOpRepositoryTest {

    private final NoOpLedgerEntryRepository ledgerRepo = new NoOpLedgerEntryRepository();
    private final NoOpActorIdentityBindingRepository bindingRepo = new NoOpActorIdentityBindingRepository();
    private final NoOpActorTrustScoreRepository trustRepo = new NoOpActorTrustScoreRepository();

    // ── NoOpLedgerEntryRepository ─────────────────────────────────────────────

    @Test
    void ledgerRepo_save_returnsEntryUnchanged() {
        final TestEntry entry = entry();
        assertThat(ledgerRepo.save(entry, "tenant")).isSameAs(entry);
    }

    @Test
    void ledgerRepo_save_doesNotAssignSequenceNumber() {
        final TestEntry entry = entry();
        entry.sequenceNumber = 99;
        ledgerRepo.save(entry, "tenant");
        assertThat(entry.sequenceNumber).isEqualTo(99);
    }

    @Test
    void ledgerRepo_findBySubjectId_returnsEmpty() {
        assertThat(ledgerRepo.findBySubjectId(UUID.randomUUID(), "tenant")).isEmpty();
    }

    @Test
    void ledgerRepo_findBySubjectIdAndTimeRange_returnsEmpty() {
        assertThat(ledgerRepo.findBySubjectIdAndTimeRange(
                UUID.randomUUID(), Instant.EPOCH, Instant.now(), "tenant")).isEmpty();
    }

    @Test
    void ledgerRepo_findLatestBySubjectId_returnsEmpty() {
        assertThat(ledgerRepo.findLatestBySubjectId(UUID.randomUUID(), "tenant")).isEmpty();
    }

    @Test
    void ledgerRepo_findEntryById_returnsEmpty() {
        assertThat(ledgerRepo.findEntryById(UUID.randomUUID(), "tenant")).isEmpty();
    }

    @Test
    void ledgerRepo_findAttestationsByEntryId_returnsEmpty() {
        assertThat(ledgerRepo.findAttestationsByEntryId(UUID.randomUUID(), "tenant")).isEmpty();
    }

    @Test
    void ledgerRepo_saveAttestation_returnsAttestationUnchanged() {
        final LedgerAttestation a = attestation();
        assertThat(ledgerRepo.saveAttestation(a, "tenant")).isSameAs(a);
    }

    @Test
    void ledgerRepo_findByActorId_returnsEmpty() {
        assertThat(ledgerRepo.findByActorId("actor", Instant.EPOCH, Instant.now(), "tenant")).isEmpty();
    }

    @Test
    void ledgerRepo_findByActorRole_returnsEmpty() {
        assertThat(ledgerRepo.findByActorRole("role", Instant.EPOCH, Instant.now(), "tenant")).isEmpty();
    }

    @Test
    void ledgerRepo_findCausedBy_returnsEmpty() {
        assertThat(ledgerRepo.findCausedBy(UUID.randomUUID(), "tenant")).isEmpty();
    }

    @Test
    void ledgerRepo_findAttestationsByEntryIdAndCapabilityTag_returnsEmpty() {
        assertThat(ledgerRepo.findAttestationsByEntryIdAndCapabilityTag(
                UUID.randomUUID(), "*", "tenant")).isEmpty();
    }

    @Test
    void ledgerRepo_findAttestationsByEntryIdGlobal_returnsEmpty() {
        assertThat(ledgerRepo.findAttestationsByEntryIdGlobal(UUID.randomUUID(), "tenant")).isEmpty();
    }

    @Test
    void ledgerRepo_findAttestationsByAttestorIdAndCapabilityTag_returnsEmpty() {
        assertThat(ledgerRepo.findAttestationsByAttestorIdAndCapabilityTag(
                "attestor", "*", "tenant")).isEmpty();
    }

    // ── NoOpActorIdentityBindingRepository ───────────────────────────────────

    @Test
    void bindingRepo_latestBindingFor_returnsEmpty() {
        assertThat(bindingRepo.latestBindingFor("actor", "tenant")).isEmpty();
    }

    @Test
    void bindingRepo_bindingHistoryFor_returnsEmpty() {
        assertThat(bindingRepo.bindingHistoryFor("actor", "tenant")).isEmpty();
    }

    // ── NoOpActorTrustScoreRepository ─────────────────────────────────────────

    @Test
    void trustRepo_findByActorId_returnsEmpty() {
        assertThat(trustRepo.findByActorId("actor")).isEmpty();
    }

    @Test
    void trustRepo_findCapabilityScore_returnsEmpty() {
        assertThat(trustRepo.findCapabilityScore("actor", "review")).isEmpty();
    }

    @Test
    void trustRepo_findDimensionScore_returnsEmpty() {
        assertThat(trustRepo.findDimensionScore("actor", "accuracy")).isEmpty();
    }

    @Test
    void trustRepo_findCapabilityDimension_returnsEmpty() {
        assertThat(trustRepo.findCapabilityDimension("actor", "review", "accuracy")).isEmpty();
    }

    @Test
    void trustRepo_findCapabilityDimensions_returnsEmptyList() {
        assertThat(trustRepo.findCapabilityDimensions("actor", "review")).isEmpty();
    }

    @Test
    void trustRepo_findByActorIdAndScoreType_returnsEmptyList() {
        assertThat(trustRepo.findByActorIdAndScoreType("actor", ScoreType.GLOBAL)).isEmpty();
    }

    @Test
    void trustRepo_upsert_doesNotThrow() {
        assertThatCode(() -> trustRepo.upsert(
                "actor", ScoreType.GLOBAL, null, null,
                ActorType.AGENT, 0.5, 10, 2, 1.5, 0.5,
                8, 2, Instant.now()))
                .doesNotThrowAnyException();
    }

    @Test
    void trustRepo_updateGlobalTrustScore_doesNotThrow() {
        assertThatCode(() -> trustRepo.updateGlobalTrustScore("actor", 0.8))
                .doesNotThrowAnyException();
    }

    @Test
    void trustRepo_findAll_returnsEmptyList() {
        assertThat(trustRepo.findAll()).isEmpty();
    }

    @Test
    void trustRepo_findAllByLastComputedAtAfter_returnsEmptyList() {
        assertThat(trustRepo.findAllByLastComputedAtAfter(Instant.EPOCH)).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private TestEntry entry() {
        final TestEntry e = new TestEntry();
        e.subjectId = UUID.randomUUID();
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = "test-actor";
        e.actorType = ActorType.SYSTEM;
        return e;
    }

    private LedgerAttestation attestation() {
        final LedgerAttestation a = new LedgerAttestation();
        a.id = UUID.randomUUID();
        a.ledgerEntryId = UUID.randomUUID();
        a.subjectId = UUID.randomUUID();
        a.attestorId = "attestor";
        a.attestorType = ActorType.SYSTEM;
        a.verdict = io.casehub.ledger.api.model.AttestationVerdict.SOUND;
        a.confidence = 1.0;
        a.occurredAt = Instant.now();
        return a;
    }

}

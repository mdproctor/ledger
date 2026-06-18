package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.CapabilityTag;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.CrossTenantLedgerEntryRepository;
import io.casehub.ledger.runtime.service.AllAttestationsGlobalStrategy;
import io.casehub.ledger.runtime.service.AttestationAggregator;
import io.casehub.ledger.runtime.service.ComputedTrustScoreSource;
import io.casehub.ledger.runtime.service.TrustScoreCalculator;
import io.casehub.platform.api.identity.ActorType;

/**
 * Tests for {@link ComputedTrustScoreSource} — on-read computation from attestation history.
 * Uses a minimal stub repository. No Quarkus runtime.
 */
class ComputedTrustScoreSourceTest {

    private StubLedgerEntryRepository ledgerRepo;
    private ComputedTrustScoreSource source;

    @BeforeEach
    void setUp() {
        ledgerRepo = new StubLedgerEntryRepository();
        final TrustScoreCalculator calculator = new TrustScoreCalculator(
                (age, verdict) -> 1.0,
                new AttestationAggregator(),
                new AllAttestationsGlobalStrategy(),
                AttestationAggregator.Strategy.WEIGHTED_MAJORITY);
        source = new ComputedTrustScoreSource(ledgerRepo, calculator);
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static class TestLedgerEntry extends LedgerEntry {
    }

    private TestLedgerEntry decision(final String actorId) {
        final TestLedgerEntry e = new TestLedgerEntry();
        e.id = UUID.randomUUID();
        e.subjectId = UUID.randomUUID();
        e.sequenceNumber = 1;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = actorId;
        e.actorType = ActorType.AGENT;
        e.actorRole = "reviewer";
        e.occurredAt = Instant.now();
        return e;
    }

    private LedgerAttestation attestation(final UUID entryId, final UUID subjectId,
            final AttestationVerdict verdict, final String capabilityTag) {
        final LedgerAttestation a = new LedgerAttestation();
        a.id = UUID.randomUUID();
        a.ledgerEntryId = entryId;
        a.subjectId = subjectId;
        a.attestorId = "peer";
        a.attestorType = ActorType.HUMAN;
        a.attestorRole = "reviewer";
        a.verdict = verdict;
        a.confidence = 1.0;
        a.capabilityTag = capabilityTag;
        a.occurredAt = Instant.now();
        return a;
    }

    private LedgerAttestation dimensionAttestation(final UUID entryId, final UUID subjectId,
            final String dimension, final double score, final String capabilityTag) {
        final LedgerAttestation a = attestation(entryId, subjectId, AttestationVerdict.SOUND, capabilityTag);
        a.trustDimension = dimension;
        a.dimensionScore = score;
        return a;
    }

    private void seedEntryWithAttestation(final String actorId, final AttestationVerdict verdict,
            final String capabilityTag) {
        final TestLedgerEntry entry = decision(actorId);
        ledgerRepo.addEntry(entry);
        ledgerRepo.addAttestation(attestation(entry.id, entry.subjectId, verdict, capabilityTag));
    }

    // ── Empty actor contract ─────────────────────────────────────────────────

    @Test
    void globalScore_noEvents_returnsEmpty() {
        assertThat(source.globalScore("unknown")).isEmpty();
    }

    @Test
    void capabilityScore_noEvents_returnsEmpty() {
        assertThat(source.capabilityScore("unknown", "review")).isEmpty();
    }

    @Test
    void decisionCount_noEvents_returnsZero() {
        assertThat(source.decisionCount("unknown", "review")).isEqualTo(0);
    }

    @Test
    void allCapabilityScores_noEvents_returnsEmptyMap() {
        assertThat(source.allCapabilityScores("unknown")).isEmpty();
    }

    // ── Capability score computation ─────────────────────────────────────────

    @Test
    void capabilityScore_computesFromAttestations() {
        seedEntryWithAttestation("alice", AttestationVerdict.SOUND, "review");

        final OptionalDouble score = source.capabilityScore("alice", "review");

        assertThat(score).isPresent();
        assertThat(score.getAsDouble()).isCloseTo(2.0 / 3.0, within(0.01));
    }

    @Test
    void capabilityScore_unknownCapability_returnsEmpty() {
        seedEntryWithAttestation("alice", AttestationVerdict.SOUND, "review");

        assertThat(source.capabilityScore("alice", "triage")).isEmpty();
    }

    @Test
    void decisionCount_matchesCapabilityScore() {
        seedEntryWithAttestation("alice", AttestationVerdict.SOUND, "review");

        assertThat(source.decisionCount("alice", "review")).isEqualTo(1);
    }

    @Test
    void decisionCount_unknownCapability_returnsZero() {
        seedEntryWithAttestation("alice", AttestationVerdict.SOUND, "review");

        assertThat(source.decisionCount("alice", "triage")).isEqualTo(0);
    }

    // ── Global score computation ─────────────────────────────────────────────

    @Test
    void globalScore_computesFromAllAttestations() {
        seedEntryWithAttestation("alice", AttestationVerdict.SOUND, "review");

        final OptionalDouble score = source.globalScore("alice");

        assertThat(score).isPresent();
        assertThat(score.getAsDouble()).isCloseTo(2.0 / 3.0, within(0.01));
    }

    // ── Dimension score computation ──────────────────────────────────────────

    @Test
    void dimensionScore_computesFromAttestations() {
        final TestLedgerEntry entry = decision("alice");
        ledgerRepo.addEntry(entry);
        ledgerRepo.addAttestation(
                dimensionAttestation(entry.id, entry.subjectId, "thoroughness", 0.8, CapabilityTag.GLOBAL));

        final OptionalDouble score = source.dimensionScore("alice", "thoroughness");

        assertThat(score).isPresent();
        assertThat(score.getAsDouble()).isCloseTo(0.8, within(0.01));
    }

    // ── Capability×Dimension score computation ───────────────────────────────

    @Test
    void capabilityDimensionScore_computesFromAttestations() {
        final TestLedgerEntry entry = decision("alice");
        ledgerRepo.addEntry(entry);
        ledgerRepo.addAttestation(
                dimensionAttestation(entry.id, entry.subjectId, "thoroughness", 0.9, "review"));

        final OptionalDouble score = source.capabilityDimensionScore("alice", "review", "thoroughness");

        assertThat(score).isPresent();
        assertThat(score.getAsDouble()).isCloseTo(0.9, within(0.01));
    }

    // ── Batch methods ────────────────────────────────────────────────────────

    @Test
    void allCapabilityScores_returnsAllComputed() {
        seedEntryWithAttestation("alice", AttestationVerdict.SOUND, "review");
        seedEntryWithAttestation("alice", AttestationVerdict.FLAGGED, "triage");

        final Map<String, Double> scores = source.allCapabilityScores("alice");

        assertThat(scores).containsKeys("review", "triage");
        assertThat(scores.get("review")).isGreaterThan(0.5);
        assertThat(scores.get("triage")).isLessThan(0.5);
    }

    // ── Per-actor computation cache ──────────────────────────────────────────

    @Test
    void multipleCallsForSameActor_returnCachedResults() {
        seedEntryWithAttestation("alice", AttestationVerdict.SOUND, "review");

        final OptionalDouble first = source.capabilityScore("alice", "review");
        final OptionalDouble second = source.capabilityScore("alice", "review");
        final int count = source.decisionCount("alice", "review");

        assertThat(first).isEqualTo(second);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void invalidateActor_causesFreshComputation() {
        seedEntryWithAttestation("alice", AttestationVerdict.SOUND, "review");

        final OptionalDouble before = source.capabilityScore("alice", "review");

        seedEntryWithAttestation("alice", AttestationVerdict.SOUND, "review");
        source.invalidateActor("alice");

        final OptionalDouble after = source.capabilityScore("alice", "review");

        assertThat(before).isPresent();
        assertThat(after).isPresent();
        assertThat(after.getAsDouble()).isGreaterThan(before.getAsDouble());
    }

    // ── findAttestationsByActorId SPI contract ─────────────────────────────

    @Test
    void findAttestationsByActorId_returnsGroupedAttestations() {
        final TestLedgerEntry entry = decision("alice");
        ledgerRepo.addEntry(entry);
        final LedgerAttestation att = attestation(entry.id, entry.subjectId,
                AttestationVerdict.SOUND, "review");
        ledgerRepo.addAttestation(att);

        final Map<UUID, List<LedgerAttestation>> result =
                ledgerRepo.findAttestationsByActorId("alice");

        assertThat(result).containsKey(entry.id);
        assertThat(result.get(entry.id)).hasSize(1);
        assertThat(result.get(entry.id).get(0).id).isEqualTo(att.id);
    }

    @Test
    void findAttestationsByActorId_excludesNonEventEntries() {
        final TestLedgerEntry command = decision("alice");
        command.entryType = LedgerEntryType.COMMAND;
        ledgerRepo.addEntry(command);
        ledgerRepo.addAttestation(attestation(command.id, command.subjectId,
                AttestationVerdict.SOUND, "review"));

        final Map<UUID, List<LedgerAttestation>> result =
                ledgerRepo.findAttestationsByActorId("alice");

        assertThat(result).isEmpty();
    }

    @Test
    void findAttestationsByActorId_excludesOtherActors() {
        final TestLedgerEntry aliceEntry = decision("alice");
        ledgerRepo.addEntry(aliceEntry);
        ledgerRepo.addAttestation(attestation(aliceEntry.id, aliceEntry.subjectId,
                AttestationVerdict.SOUND, "review"));
        final TestLedgerEntry bobEntry = decision("bob");
        ledgerRepo.addEntry(bobEntry);
        ledgerRepo.addAttestation(attestation(bobEntry.id, bobEntry.subjectId,
                AttestationVerdict.FLAGGED, "review"));

        final Map<UUID, List<LedgerAttestation>> result =
                ledgerRepo.findAttestationsByActorId("alice");

        assertThat(result).containsOnlyKeys(aliceEntry.id);
    }

    // ── Stub repository ──────────────────────────────────────────────────────

    private static class StubLedgerEntryRepository implements CrossTenantLedgerEntryRepository {
        private final List<LedgerEntry> entries = new ArrayList<>();
        private final List<LedgerAttestation> attestations = new ArrayList<>();

        void addEntry(final LedgerEntry e) { entries.add(e); }
        void addAttestation(final LedgerAttestation a) { attestations.add(a); }

        @Override
        public List<LedgerEntry> findEventsByActorId(final String actorId) {
            return entries.stream()
                    .filter(e -> actorId.equals(e.actorId) && e.entryType == LedgerEntryType.EVENT)
                    .toList();
        }

        @Override
        public Map<UUID, List<LedgerAttestation>> findAttestationsForEntries(final Set<UUID> entryIds) {
            final Map<UUID, List<LedgerAttestation>> result = new HashMap<>();
            for (final LedgerAttestation a : attestations) {
                if (entryIds.contains(a.ledgerEntryId)) {
                    result.computeIfAbsent(a.ledgerEntryId, k -> new ArrayList<>()).add(a);
                }
            }
            return result;
        }

        @Override
        public Map<UUID, List<LedgerAttestation>> findAttestationsByActorId(final String actorId) {
            final Set<UUID> eventIds = entries.stream()
                    .filter(e -> actorId.equals(e.actorId) && e.entryType == LedgerEntryType.EVENT)
                    .map(e -> e.id)
                    .collect(java.util.stream.Collectors.toSet());
            return findAttestationsForEntries(eventIds);
        }

        @Override public List<LedgerEntry> listAll() { return entries; }
        @Override public List<LedgerEntry> findAllEvents() { return entries.stream().filter(e -> e.entryType == LedgerEntryType.EVENT).toList(); }
        @Override public List<LedgerEntry> findByTimeRange(final Instant f, final Instant t) { return List.of(); }
        @Override public List<io.casehub.ledger.runtime.service.model.SubjectSequenceStats> findSequenceStats() { return List.of(); }
    }
}

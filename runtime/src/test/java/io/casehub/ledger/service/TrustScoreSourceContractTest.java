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
import java.util.stream.Stream;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.CapabilityTag;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.model.ActorTrustScore.ScoreType;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.ledger.runtime.repository.CrossTenantLedgerEntryRepository;
import io.casehub.ledger.runtime.service.AllAttestationsGlobalStrategy;
import io.casehub.ledger.runtime.service.AttestationAggregator;
import io.casehub.ledger.runtime.service.CachedTrustScoreSource;
import io.casehub.ledger.runtime.service.ComputedTrustScoreSource;
import io.casehub.ledger.runtime.service.DecayFunction;
import io.casehub.ledger.runtime.service.MaterializedTrustScoreSource;
import io.casehub.ledger.runtime.service.TrustScoreCalculator;
import io.casehub.ledger.runtime.service.TrustScoreCalculator.ComputedScores;
import io.casehub.platform.api.identity.ActorType;

/**
 * Contract test: verifies all three {@link TrustScoreSource} implementations agree
 * when given the same attestation history.
 *
 * <p>Setup pattern: seed entries + attestations → run PerActorTrustComputer for
 * materialized/cached → assert all three sources return equivalent results.
 */
class TrustScoreSourceContractTest {

    private static final DecayFunction NO_DECAY = (age, verdict) -> 1.0;
    private static final String ACTOR = "agent-contract";
    private static final Instant NOW = Instant.now();

    // ── Shared test data ─────────────────────────────────────────────────────

    private static class TestLedgerEntry extends LedgerEntry {
    }

    private static final TestLedgerEntry D1 = decision("d1");
    private static final TestLedgerEntry D2 = decision("d2");

    private static final LedgerAttestation A_REVIEW_SOUND = capAttestation(D1, AttestationVerdict.SOUND, "review");
    private static final LedgerAttestation A_TRIAGE_FLAGGED = capAttestation(D1, AttestationVerdict.FLAGGED, "triage");
    private static final LedgerAttestation A_REVIEW_SOUND_2 = capAttestation(D2, AttestationVerdict.SOUND, "review");
    private static final LedgerAttestation A_DIM_THOROUGHNESS = dimAttestation(D1, "thoroughness", 0.8, "review");

    private static final List<LedgerEntry> DECISIONS = List.of(D1, D2);
    private static final List<LedgerAttestation> ALL_ATTESTATIONS =
            List.of(A_REVIEW_SOUND, A_TRIAGE_FLAGGED, A_REVIEW_SOUND_2, A_DIM_THOROUGHNESS);

    private static final Map<UUID, List<LedgerAttestation>> BY_ENTRY = buildByEntry();

    // ── Source factory ────────────────────────────────────────────────────────

    static Stream<Named<TrustScoreSource>> sources() {
        final TrustScoreCalculator calculator = new TrustScoreCalculator(
                NO_DECAY, new AttestationAggregator(), new AllAttestationsGlobalStrategy(),
                AttestationAggregator.Strategy.WEIGHTED_MAJORITY);

        // --- Materialized: compute scores via calculator, then populate repo ---
        final InlineRepo repo = new InlineRepo();
        final ComputedScores computed_scores = calculator.computeAll(DECISIONS, BY_ENTRY, NOW);
        for (final var e : computed_scores.capabilityScores().entrySet()) {
            repo.upsert(ACTOR, ScoreType.CAPABILITY, e.getKey(), null, ActorType.AGENT,
                    e.getValue().trustScore(), e.getValue().decisionCount(), e.getValue().overturnedCount(),
                    e.getValue().alpha(), e.getValue().beta(),
                    e.getValue().attestationPositive(), e.getValue().attestationNegative(), NOW);
        }
        for (final var e : computed_scores.dimensionScores().entrySet()) {
            repo.upsert(ACTOR, ScoreType.DIMENSION, null, e.getKey(), ActorType.AGENT,
                    e.getValue(), 0, 0, 0.0, 0.0, 0, 0, NOW);
        }
        for (final var capEntry : computed_scores.capabilityDimensionScores().entrySet()) {
            for (final var dimEntry : capEntry.getValue().entrySet()) {
                repo.upsert(ACTOR, ScoreType.CAPABILITY_DIMENSION, capEntry.getKey(), dimEntry.getKey(),
                        ActorType.AGENT, dimEntry.getValue(), 0, 0, 0.0, 0.0, 0, 0, NOW);
            }
        }
        final var gs = computed_scores.globalScore();
        repo.upsert(ACTOR, ScoreType.GLOBAL, null, null, ActorType.AGENT,
                gs.trustScore(), gs.decisionCount(), gs.overturnedCount(),
                gs.alpha(), gs.beta(), gs.attestationPositive(), gs.attestationNegative(), NOW);
        final MaterializedTrustScoreSource materialized = new MaterializedTrustScoreSource(repo);

        // --- Cached ---
        final CachedTrustScoreSource cached = new CachedTrustScoreSource(repo);
        cached.hydrate();

        // --- Computed ---
        final InlineLedgerRepo ledgerRepo = new InlineLedgerRepo(DECISIONS, ALL_ATTESTATIONS);
        final ComputedTrustScoreSource computed = new ComputedTrustScoreSource(ledgerRepo, calculator);

        return Stream.of(
                Named.of("Materialized", materialized),
                Named.of("Cached", cached),
                Named.of("Computed", computed));
    }

    // ── Contract: all sources agree ──────────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    void globalScore_returnsConsistentValue(final TrustScoreSource source) {
        final OptionalDouble score = source.globalScore(ACTOR);
        assertThat(score).isPresent();
        assertThat(score.getAsDouble()).isCloseTo(0.6, within(0.1));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    void capabilityScore_review_returnsConsistentValue(final TrustScoreSource source) {
        final OptionalDouble score = source.capabilityScore(ACTOR, "review");
        assertThat(score).isPresent();
        assertThat(score.getAsDouble()).isGreaterThan(0.5);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    void capabilityScore_triage_returnsConsistentValue(final TrustScoreSource source) {
        final OptionalDouble score = source.capabilityScore(ACTOR, "triage");
        assertThat(score).isPresent();
        assertThat(score.getAsDouble()).isLessThan(0.5);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    void dimensionScore_thoroughness_returnsConsistentValue(final TrustScoreSource source) {
        final OptionalDouble score = source.dimensionScore(ACTOR, "thoroughness");
        assertThat(score).isPresent();
        assertThat(score.getAsDouble()).isCloseTo(0.8, within(0.05));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    void capabilityDimensionScore_reviewThoroughness_returnsConsistentValue(final TrustScoreSource source) {
        final OptionalDouble score = source.capabilityDimensionScore(ACTOR, "review", "thoroughness");
        assertThat(score).isPresent();
        assertThat(score.getAsDouble()).isCloseTo(0.8, within(0.05));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    void decisionCount_review_returnsConsistentValue(final TrustScoreSource source) {
        assertThat(source.decisionCount(ACTOR, "review")).isGreaterThan(0);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    void allCapabilityScores_returnsConsistentKeys(final TrustScoreSource source) {
        final Map<String, Double> scores = source.allCapabilityScores(ACTOR);
        assertThat(scores).containsKeys("review", "triage");
    }

    // ── Edge case: actor with events but zero attestations ─────────────────

    static Stream<Named<TrustScoreSource>> sourcesWithUnattestedActor() {
        final TrustScoreCalculator calculator = new TrustScoreCalculator(
                NO_DECAY, new AttestationAggregator(), new AllAttestationsGlobalStrategy(),
                AttestationAggregator.Strategy.WEIGHTED_MAJORITY);

        final String unattestedActor = "agent-unattested";
        final TestLedgerEntry unattestedEntry = decision("unattested");
        unattestedEntry.actorId = unattestedActor;

        // Materialized: compute for the unattested actor (produces prior scores)
        final InlineRepo repo = new InlineRepo();
        final var scores = calculator.computeAll(List.of(unattestedEntry), Map.of(), NOW);
        final var gs = scores.globalScore();
        repo.upsert(unattestedActor, ScoreType.GLOBAL, null, null, ActorType.AGENT,
                gs.trustScore(), gs.decisionCount(), gs.overturnedCount(),
                gs.alpha(), gs.beta(), gs.attestationPositive(), gs.attestationNegative(), NOW);
        final MaterializedTrustScoreSource materialized = new MaterializedTrustScoreSource(repo);

        // Cached
        final CachedTrustScoreSource cached = new CachedTrustScoreSource(repo);
        cached.hydrate();

        // Computed
        final InlineLedgerRepo ledgerRepo = new InlineLedgerRepo(List.of(unattestedEntry), List.of());
        final ComputedTrustScoreSource computed = new ComputedTrustScoreSource(ledgerRepo, calculator);

        return Stream.of(
                Named.of("Materialized", materialized),
                Named.of("Cached", cached),
                Named.of("Computed", computed));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sourcesWithUnattestedActor")
    void unattestedActor_capabilityScore_returnsEmpty(final TrustScoreSource source) {
        assertThat(source.capabilityScore("agent-unattested", "review")).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sourcesWithUnattestedActor")
    void unattestedActor_decisionCount_returnsZero(final TrustScoreSource source) {
        assertThat(source.decisionCount("agent-unattested", "review")).isEqualTo(0);
    }

    // ── Edge case: zero-event actor ──────────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    void unknownActor_globalScore_returnsEmpty(final TrustScoreSource source) {
        assertThat(source.globalScore("nonexistent")).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    void unknownActor_decisionCount_returnsZero(final TrustScoreSource source) {
        assertThat(source.decisionCount("nonexistent", "review")).isEqualTo(0);
    }

    // ── Edge case: unknown capability for known actor ────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    void knownActor_unknownCapability_returnsEmpty(final TrustScoreSource source) {
        assertThat(source.capabilityScore(ACTOR, "does-not-exist")).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    void knownActor_unknownCapability_decisionCount_returnsZero(final TrustScoreSource source) {
        assertThat(source.decisionCount(ACTOR, "does-not-exist")).isEqualTo(0);
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static TestLedgerEntry decision(final String tag) {
        final TestLedgerEntry e = new TestLedgerEntry();
        e.id = UUID.nameUUIDFromBytes(tag.getBytes());
        e.subjectId = UUID.randomUUID();
        e.sequenceNumber = 1;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = ACTOR;
        e.actorType = ActorType.AGENT;
        e.actorRole = "reviewer";
        e.occurredAt = NOW;
        return e;
    }

    private static LedgerAttestation capAttestation(final LedgerEntry entry,
            final AttestationVerdict verdict, final String capabilityTag) {
        final LedgerAttestation a = new LedgerAttestation();
        a.id = UUID.randomUUID();
        a.ledgerEntryId = entry.id;
        a.subjectId = entry.subjectId;
        a.attestorId = "peer";
        a.attestorType = ActorType.HUMAN;
        a.attestorRole = "reviewer";
        a.verdict = verdict;
        a.confidence = 1.0;
        a.capabilityTag = capabilityTag;
        a.occurredAt = NOW;
        return a;
    }

    private static LedgerAttestation dimAttestation(final LedgerEntry entry,
            final String dimension, final double score, final String capabilityTag) {
        final LedgerAttestation a = capAttestation(entry, AttestationVerdict.SOUND, capabilityTag);
        a.trustDimension = dimension;
        a.dimensionScore = score;
        return a;
    }

    private static Map<UUID, List<LedgerAttestation>> buildByEntry() {
        final Map<UUID, List<LedgerAttestation>> map = new HashMap<>();
        for (final LedgerAttestation a : ALL_ATTESTATIONS) {
            map.computeIfAbsent(a.ledgerEntryId, k -> new ArrayList<>()).add(a);
        }
        return map;
    }

    // ── Inline repositories ──────────────────────────────────────────────────

    private static class InlineRepo implements ActorTrustScoreRepository {
        private final List<ActorTrustScore> scores = new ArrayList<>();

        @Override
        public void upsert(final String actorId, final ScoreType scoreType,
                final String capabilityKey, final String dimensionKey,
                final ActorType actorType, final double trustScore,
                final int decisionCount, final int overturnedCount,
                final double alpha, final double beta,
                final int attestationPositive, final int attestationNegative,
                final Instant lastComputedAt) {
            scores.removeIf(s -> s.actorId.equals(actorId) && s.scoreType == scoreType
                    && java.util.Objects.equals(s.capabilityKey, capabilityKey)
                    && java.util.Objects.equals(s.dimensionKey, dimensionKey));
            final ActorTrustScore s = new ActorTrustScore();
            s.id = UUID.randomUUID();
            s.actorId = actorId;
            s.scoreType = scoreType;
            s.capabilityKey = capabilityKey;
            s.dimensionKey = dimensionKey;
            s.actorType = actorType;
            s.trustScore = trustScore;
            s.decisionCount = decisionCount;
            s.overturnedCount = overturnedCount;
            s.alpha = alpha;
            s.beta = beta;
            s.attestationPositive = attestationPositive;
            s.attestationNegative = attestationNegative;
            s.lastComputedAt = lastComputedAt;
            scores.add(s);
        }

        @Override public Optional<ActorTrustScore> findByActorId(final String id) {
            return scores.stream().filter(s -> s.actorId.equals(id) && s.scoreType == ScoreType.GLOBAL).findFirst();
        }
        @Override public Optional<ActorTrustScore> findCapabilityScore(final String id, final String t) {
            return scores.stream().filter(s -> s.actorId.equals(id) && s.scoreType == ScoreType.CAPABILITY && t.equals(s.capabilityKey)).findFirst();
        }
        @Override public Optional<ActorTrustScore> findDimensionScore(final String id, final String d) {
            return scores.stream().filter(s -> s.actorId.equals(id) && s.scoreType == ScoreType.DIMENSION && d.equals(s.dimensionKey)).findFirst();
        }
        @Override public Optional<ActorTrustScore> findCapabilityDimension(final String id, final String c, final String d) {
            return scores.stream().filter(s -> s.actorId.equals(id) && s.scoreType == ScoreType.CAPABILITY_DIMENSION && c.equals(s.capabilityKey) && d.equals(s.dimensionKey)).findFirst();
        }
        @Override public List<ActorTrustScore> findCapabilityDimensions(final String id, final String c) {
            return scores.stream().filter(s -> s.actorId.equals(id) && s.scoreType == ScoreType.CAPABILITY_DIMENSION && c.equals(s.capabilityKey)).toList();
        }
        @Override public List<ActorTrustScore> findByActorIdAndScoreType(final String id, final ScoreType t) {
            return scores.stream().filter(s -> s.actorId.equals(id) && s.scoreType == t).toList();
        }
        @Override public void updateGlobalTrustScore(final String a, final double g) { }
        @Override public List<ActorTrustScore> findAll() { return scores; }
        @Override public List<ActorTrustScore> findAllByLastComputedAtAfter(final Instant s) { return List.of(); }
    }

    private static class InlineLedgerRepo implements CrossTenantLedgerEntryRepository {
        private final List<LedgerEntry> entries;
        private final List<LedgerAttestation> attestations;

        InlineLedgerRepo(final List<LedgerEntry> entries, final List<LedgerAttestation> attestations) {
            this.entries = entries;
            this.attestations = attestations;
        }

        @Override public List<LedgerEntry> findEventsByActorId(final String actorId) {
            return entries.stream().filter(e -> actorId.equals(e.actorId) && e.entryType == LedgerEntryType.EVENT).toList();
        }
        @Override public Map<UUID, List<LedgerAttestation>> findAttestationsForEntries(final Set<UUID> entryIds) {
            final Map<UUID, List<LedgerAttestation>> r = new HashMap<>();
            for (final LedgerAttestation a : attestations) {
                if (entryIds.contains(a.ledgerEntryId)) r.computeIfAbsent(a.ledgerEntryId, k -> new ArrayList<>()).add(a);
            }
            return r;
        }
        @Override public Map<UUID, List<LedgerAttestation>> findAttestationsByActorId(final String actorId) {
            final Set<UUID> eventIds = entries.stream()
                    .filter(e -> actorId.equals(e.actorId) && e.entryType == LedgerEntryType.EVENT)
                    .map(e -> e.id).collect(java.util.stream.Collectors.toSet());
            return findAttestationsForEntries(eventIds);
        }
        @Override public List<LedgerEntry> listAll() { return entries; }
        @Override public List<LedgerEntry> findAllEvents() { return entries.stream().filter(e -> e.entryType == LedgerEntryType.EVENT).toList(); }
        @Override public List<LedgerEntry> findByTimeRange(final Instant f, final Instant t) { return List.of(); }
    }
}

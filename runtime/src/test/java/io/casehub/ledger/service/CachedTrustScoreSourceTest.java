package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.ActorTrustScore.ScoreType;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.ledger.runtime.service.CachedTrustScoreSource;
import io.casehub.ledger.runtime.service.routing.TrustScoreActorUpdatedEvent;
import io.casehub.ledger.runtime.service.routing.TrustScoreFullPayload;
import io.casehub.platform.api.identity.ActorType;

/**
 * Tests for {@link CachedTrustScoreSource} — in-memory cache with event-driven refresh.
 */
class CachedTrustScoreSourceTest {

    private StubTrustScoreRepository repo;
    private CachedTrustScoreSource source;

    @BeforeEach
    void setUp() {
        repo = new StubTrustScoreRepository();
        source = new CachedTrustScoreSource(repo);
    }

    // ── Startup hydration ────────────────────────────────────────────────────

    @Test
    void afterHydrate_globalScore_returnsValue() {
        repo.seed(score("alice", ScoreType.GLOBAL, null, null, 0.75, 10));
        source.hydrate();

        assertThat(source.globalScore("alice")).hasValue(0.75);
    }

    @Test
    void afterHydrate_capabilityScore_returnsValue() {
        repo.seed(score("alice", ScoreType.CAPABILITY, "review", null, 0.8, 5));
        source.hydrate();

        assertThat(source.capabilityScore("alice", "review")).hasValue(0.8);
    }

    @Test
    void afterHydrate_dimensionScore_returnsValue() {
        repo.seed(score("alice", ScoreType.DIMENSION, null, "thoroughness", 0.9, 3));
        source.hydrate();

        assertThat(source.dimensionScore("alice", "thoroughness")).hasValue(0.9);
    }

    @Test
    void afterHydrate_capabilityDimensionScore_returnsValue() {
        repo.seed(score("alice", ScoreType.CAPABILITY_DIMENSION, "review", "thoroughness", 0.85, 2));
        source.hydrate();

        assertThat(source.capabilityDimensionScore("alice", "review", "thoroughness")).hasValue(0.85);
    }

    @Test
    void afterHydrate_decisionCount_returnsValue() {
        repo.seed(score("alice", ScoreType.CAPABILITY, "review", null, 0.8, 7));
        source.hydrate();

        assertThat(source.decisionCount("alice", "review")).isEqualTo(7);
    }

    // ── Empty actor ──────────────────────────────────────────────────────────

    @Test
    void unknownActor_globalScore_returnsEmpty() {
        source.hydrate();
        assertThat(source.globalScore("unknown")).isEmpty();
    }

    @Test
    void unknownActor_decisionCount_returnsZero() {
        source.hydrate();
        assertThat(source.decisionCount("unknown", "review")).isEqualTo(0);
    }

    // ── Batch methods ────────────────────────────────────────────────────────

    @Test
    void allCapabilityScores_returnsAll() {
        repo.seed(score("alice", ScoreType.CAPABILITY, "review", null, 0.8, 5));
        repo.seed(score("alice", ScoreType.CAPABILITY, "triage", null, 0.6, 3));
        source.hydrate();

        final Map<String, Double> result = source.allCapabilityScores("alice");
        assertThat(result).containsEntry("review", 0.8).containsEntry("triage", 0.6);
    }

    @Test
    void allDimensionScores_returnsAll() {
        repo.seed(score("alice", ScoreType.DIMENSION, null, "thoroughness", 0.9, 3));
        repo.seed(score("alice", ScoreType.DIMENSION, null, "accuracy", 0.7, 2));
        source.hydrate();

        final Map<String, Double> result = source.allDimensionScores("alice");
        assertThat(result).containsEntry("thoroughness", 0.9).containsEntry("accuracy", 0.7);
    }

    @Test
    void qualityScores_returnsCapabilityDimensions() {
        repo.seed(score("alice", ScoreType.CAPABILITY_DIMENSION, "review", "thoroughness", 0.85, 2));
        repo.seed(score("alice", ScoreType.CAPABILITY_DIMENSION, "review", "accuracy", 0.7, 1));
        source.hydrate();

        final Map<String, Double> result = source.qualityScores("alice", "review");
        assertThat(result).containsEntry("thoroughness", 0.85).containsEntry("accuracy", 0.7);
    }

    // ── TrustScoreFullPayload refresh ────────────────────────────────────────

    @Test
    void onFull_updatesCache() {
        source.hydrate();
        assertThat(source.globalScore("alice")).isEmpty();

        source.onFull(new TrustScoreFullPayload(
                List.of(score("alice", ScoreType.GLOBAL, null, null, 0.9, 15))));

        assertThat(source.globalScore("alice")).hasValue(0.9);
    }

    @Test
    void onFull_updatesAllScoreTypes() {
        source.hydrate();

        source.onFull(new TrustScoreFullPayload(List.of(
                score("alice", ScoreType.CAPABILITY, "review", null, 0.8, 5),
                score("alice", ScoreType.DIMENSION, null, "thoroughness", 0.9, 3),
                score("alice", ScoreType.CAPABILITY_DIMENSION, "review", "thoroughness", 0.85, 2))));

        assertThat(source.capabilityScore("alice", "review")).hasValue(0.8);
        assertThat(source.dimensionScore("alice", "thoroughness")).hasValue(0.9);
        assertThat(source.capabilityDimensionScore("alice", "review", "thoroughness")).hasValue(0.85);
    }

    // ── TrustScoreActorUpdatedEvent refresh (the bug fix) ────────────────────

    @Test
    void onActorUpdated_updatesCache() {
        source.hydrate();
        assertThat(source.capabilityScore("alice", "review")).isEmpty();

        source.onActorUpdated(new TrustScoreActorUpdatedEvent("alice",
                List.of(score("alice", ScoreType.CAPABILITY, "review", null, 0.7, 3)),
                Instant.now()));

        assertThat(source.capabilityScore("alice", "review")).hasValue(0.7);
    }

    @Test
    void onActorUpdated_updatesGlobalScore() {
        repo.seed(score("alice", ScoreType.GLOBAL, null, null, 0.5, 1));
        source.hydrate();

        source.onActorUpdated(new TrustScoreActorUpdatedEvent("alice",
                List.of(score("alice", ScoreType.GLOBAL, null, null, 0.8, 5)),
                Instant.now()));

        assertThat(source.globalScore("alice")).hasValue(0.8);
    }

    // ── Fixture ──────────────────────────────────────────────────────────────

    private static ActorTrustScore score(final String actorId, final ScoreType type,
            final String capKey, final String dimKey, final double trustScore,
            final int decisionCount) {
        final ActorTrustScore s = new ActorTrustScore();
        s.actorId = actorId;
        s.scoreType = type;
        s.capabilityKey = capKey;
        s.dimensionKey = dimKey;
        s.actorType = ActorType.AGENT;
        s.trustScore = trustScore;
        s.decisionCount = decisionCount;
        s.lastComputedAt = Instant.now();
        return s;
    }

    private static class StubTrustScoreRepository implements ActorTrustScoreRepository {
        private final java.util.List<ActorTrustScore> scores = new java.util.ArrayList<>();

        void seed(final ActorTrustScore s) { scores.add(s); }

        @Override public List<ActorTrustScore> findAll() { return scores; }
        @Override public Optional<ActorTrustScore> findByActorId(final String a) { return Optional.empty(); }
        @Override public Optional<ActorTrustScore> findCapabilityScore(final String a, final String t) { return Optional.empty(); }
        @Override public Optional<ActorTrustScore> findDimensionScore(final String a, final String d) { return Optional.empty(); }
        @Override public Optional<ActorTrustScore> findCapabilityDimension(final String a, final String c, final String d) { return Optional.empty(); }
        @Override public List<ActorTrustScore> findCapabilityDimensions(final String a, final String c) { return List.of(); }
        @Override public List<ActorTrustScore> findByActorIdAndScoreType(final String a, final ScoreType t) { return List.of(); }
        @Override public void upsert(final String a, final ScoreType t, final String c, final String d, final ActorType at, final double ts, final int dc, final int oc, final double al, final double be, final int ap, final int an, final Instant lc) { }
        @Override public void updateGlobalTrustScore(final String a, final double g) { }
        @Override public List<ActorTrustScore> findAllByLastComputedAtAfter(final Instant s) { return List.of(); }
    }
}

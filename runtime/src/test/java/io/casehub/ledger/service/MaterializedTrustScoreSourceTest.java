package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.ActorTrustScore.ScoreType;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.ledger.runtime.service.MaterializedTrustScoreSource;
import io.casehub.platform.api.identity.ActorType;

/**
 * Pure unit tests for {@link MaterializedTrustScoreSource} — no Quarkus runtime, no CDI.
 * Uses a minimal in-line stub repository.
 */
class MaterializedTrustScoreSourceTest {

    private StubTrustScoreRepository repo;
    private MaterializedTrustScoreSource source;

    @BeforeEach
    void setUp() {
        repo = new StubTrustScoreRepository();
        source = new MaterializedTrustScoreSource(repo);
    }

    // ── Empty actor ──────────────────────────────────────────────────────────

    @Test
    void globalScore_unknownActor_returnsEmpty() {
        assertThat(source.globalScore("unknown")).isEmpty();
    }

    @Test
    void capabilityScore_unknownActor_returnsEmpty() {
        assertThat(source.capabilityScore("unknown", "review")).isEmpty();
    }

    @Test
    void dimensionScore_unknownActor_returnsEmpty() {
        assertThat(source.dimensionScore("unknown", "thoroughness")).isEmpty();
    }

    @Test
    void capabilityDimensionScore_unknownActor_returnsEmpty() {
        assertThat(source.capabilityDimensionScore("unknown", "review", "thoroughness")).isEmpty();
    }

    @Test
    void decisionCount_unknownActor_returnsZero() {
        assertThat(source.decisionCount("unknown", "review")).isEqualTo(0);
    }

    @Test
    void allCapabilityScores_unknownActor_returnsEmptyMap() {
        assertThat(source.allCapabilityScores("unknown")).isEmpty();
    }

    @Test
    void allDimensionScores_unknownActor_returnsEmptyMap() {
        assertThat(source.allDimensionScores("unknown")).isEmpty();
    }

    @Test
    void qualityScores_unknownActor_returnsEmptyMap() {
        assertThat(source.qualityScores("unknown", "review")).isEmpty();
    }

    // ── Known actor with scores ──────────────────────────────────────────────

    @Test
    void globalScore_returnsStoredValue() {
        repo.seed(score("alice", ScoreType.GLOBAL, null, null, 0.75, 10));
        assertThat(source.globalScore("alice")).hasValue(0.75);
    }

    @Test
    void capabilityScore_returnsStoredValue() {
        repo.seed(score("alice", ScoreType.CAPABILITY, "review", null, 0.8, 5));
        assertThat(source.capabilityScore("alice", "review")).hasValue(0.8);
    }

    @Test
    void capabilityScore_unknownCapability_returnsEmpty() {
        repo.seed(score("alice", ScoreType.CAPABILITY, "review", null, 0.8, 5));
        assertThat(source.capabilityScore("alice", "triage")).isEmpty();
    }

    @Test
    void dimensionScore_returnsStoredValue() {
        repo.seed(score("alice", ScoreType.DIMENSION, null, "thoroughness", 0.9, 3));
        assertThat(source.dimensionScore("alice", "thoroughness")).hasValue(0.9);
    }

    @Test
    void capabilityDimensionScore_returnsStoredValue() {
        repo.seed(score("alice", ScoreType.CAPABILITY_DIMENSION, "review", "thoroughness", 0.85, 2));
        assertThat(source.capabilityDimensionScore("alice", "review", "thoroughness")).hasValue(0.85);
    }

    @Test
    void decisionCount_returnsStoredDecisionCount() {
        repo.seed(score("alice", ScoreType.CAPABILITY, "review", null, 0.8, 7));
        assertThat(source.decisionCount("alice", "review")).isEqualTo(7);
    }

    @Test
    void allCapabilityScores_returnsAllCapabilities() {
        repo.seed(score("alice", ScoreType.CAPABILITY, "review", null, 0.8, 5));
        repo.seed(score("alice", ScoreType.CAPABILITY, "triage", null, 0.6, 3));

        final Map<String, Double> result = source.allCapabilityScores("alice");
        assertThat(result).containsEntry("review", 0.8).containsEntry("triage", 0.6);
    }

    @Test
    void allDimensionScores_returnsAllDimensions() {
        repo.seed(score("alice", ScoreType.DIMENSION, null, "thoroughness", 0.9, 3));
        repo.seed(score("alice", ScoreType.DIMENSION, null, "accuracy", 0.7, 2));

        final Map<String, Double> result = source.allDimensionScores("alice");
        assertThat(result).containsEntry("thoroughness", 0.9).containsEntry("accuracy", 0.7);
    }

    @Test
    void qualityScores_returnsCapabilityDimensions() {
        repo.seed(score("alice", ScoreType.CAPABILITY_DIMENSION, "review", "thoroughness", 0.85, 2));
        repo.seed(score("alice", ScoreType.CAPABILITY_DIMENSION, "review", "accuracy", 0.7, 1));

        final Map<String, Double> result = source.qualityScores("alice", "review");
        assertThat(result).containsEntry("thoroughness", 0.85).containsEntry("accuracy", 0.7);
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

    /**
     * Minimal stub — enough to test MaterializedTrustScoreSource without pulling in
     * persistence-memory module.
     */
    private static class StubTrustScoreRepository implements ActorTrustScoreRepository {
        private final java.util.List<ActorTrustScore> scores = new java.util.ArrayList<>();

        void seed(final ActorTrustScore s) {
            scores.add(s);
        }

        @Override
        public Optional<ActorTrustScore> findByActorId(final String actorId) {
            return scores.stream()
                    .filter(s -> s.actorId.equals(actorId) && s.scoreType == ScoreType.GLOBAL)
                    .findFirst();
        }

        @Override
        public Optional<ActorTrustScore> findCapabilityScore(final String actorId, final String tag) {
            return scores.stream()
                    .filter(s -> s.actorId.equals(actorId) && s.scoreType == ScoreType.CAPABILITY
                            && tag.equals(s.capabilityKey))
                    .findFirst();
        }

        @Override
        public Optional<ActorTrustScore> findDimensionScore(final String actorId, final String dim) {
            return scores.stream()
                    .filter(s -> s.actorId.equals(actorId) && s.scoreType == ScoreType.DIMENSION
                            && dim.equals(s.dimensionKey))
                    .findFirst();
        }

        @Override
        public Optional<ActorTrustScore> findCapabilityDimension(final String actorId,
                final String cap, final String dim) {
            return scores.stream()
                    .filter(s -> s.actorId.equals(actorId)
                            && s.scoreType == ScoreType.CAPABILITY_DIMENSION
                            && cap.equals(s.capabilityKey) && dim.equals(s.dimensionKey))
                    .findFirst();
        }

        @Override
        public List<ActorTrustScore> findCapabilityDimensions(final String actorId, final String cap) {
            return scores.stream()
                    .filter(s -> s.actorId.equals(actorId)
                            && s.scoreType == ScoreType.CAPABILITY_DIMENSION
                            && cap.equals(s.capabilityKey))
                    .toList();
        }

        @Override
        public List<ActorTrustScore> findByActorIdAndScoreType(final String actorId,
                final ScoreType type) {
            return scores.stream()
                    .filter(s -> s.actorId.equals(actorId) && s.scoreType == type)
                    .toList();
        }

        @Override public void upsert(final String a, final ScoreType t, final String c,
                final String d, final ActorType at, final double ts, final int dc,
                final int oc, final double al, final double be, final int ap,
                final int an, final Instant lc) { }
        @Override public void updateGlobalTrustScore(final String a, final double g) { }
        @Override public List<ActorTrustScore> findAll() { return scores; }
        @Override public List<ActorTrustScore> findAllByLastComputedAtAfter(final Instant s) {
            return List.of();
        }
    }
}

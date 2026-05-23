package io.casehub.ledger.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.ActorTrustScore.ScoreType;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class InMemoryActorTrustScoreRepositoryTest {

    @Inject
    InMemoryActorTrustScoreRepository repo;

    @BeforeEach
    void setUp() {
        repo.clear();
    }

    @Test
    void upsert_global_andFindByActorId() {
        String actorId = "actor-" + UUID.randomUUID();
        upsert(actorId, ScoreType.GLOBAL, null, null, 0.8);

        assertThat(repo.findByActorId(actorId)).isPresent();
        assertThat(repo.findByActorId(actorId).get().trustScore).isEqualTo(0.8);
    }

    @Test
    void upsert_capability_andFindCapabilityScore() {
        String actorId = "actor-" + UUID.randomUUID();
        upsert(actorId, ScoreType.CAPABILITY, "review", null, 0.7);

        assertThat(repo.findCapabilityScore(actorId, "review")).isPresent();
        assertThat(repo.findCapabilityScore(actorId, "review").get().trustScore).isEqualTo(0.7);
        assertThat(repo.findCapabilityScore(actorId, "other")).isEmpty();
    }

    @Test
    void upsert_dimension_andFindDimensionScore() {
        String actorId = "actor-" + UUID.randomUUID();
        upsert(actorId, ScoreType.DIMENSION, null, "accuracy", 0.9);

        assertThat(repo.findDimensionScore(actorId, "accuracy")).isPresent();
        assertThat(repo.findDimensionScore(actorId, "other")).isEmpty();
    }

    @Test
    void upsert_capabilityDimension_andFind() {
        String actorId = "actor-" + UUID.randomUUID();
        upsert(actorId, ScoreType.CAPABILITY_DIMENSION, "review", "accuracy", 0.85);

        assertThat(repo.findCapabilityDimension(actorId, "review", "accuracy")).isPresent();
        assertThat(repo.findCapabilityDimension(actorId, "review", "other")).isEmpty();
    }

    @Test
    void upsert_isIdempotent_updatesExistingRow() {
        String actorId = "actor-" + UUID.randomUUID();
        upsert(actorId, ScoreType.GLOBAL, null, null, 0.5);
        upsert(actorId, ScoreType.GLOBAL, null, null, 0.9);

        assertThat(repo.findAll()).hasSize(1);
        assertThat(repo.findByActorId(actorId).get().trustScore).isEqualTo(0.9);
    }

    @Test
    void updateGlobalTrustScore_updatesField() {
        String actorId = "actor-" + UUID.randomUUID();
        upsert(actorId, ScoreType.GLOBAL, null, null, 0.5);

        repo.updateGlobalTrustScore(actorId, 0.99);

        assertThat(repo.findByActorId(actorId).get().globalTrustScore).isEqualTo(0.99);
    }

    @Test
    void findCapabilityDimensions_returnsAllForCapability() {
        String actorId = "actor-" + UUID.randomUUID();
        upsert(actorId, ScoreType.CAPABILITY_DIMENSION, "review", "accuracy", 0.8);
        upsert(actorId, ScoreType.CAPABILITY_DIMENSION, "review", "speed", 0.6);
        upsert(actorId, ScoreType.CAPABILITY_DIMENSION, "other", "accuracy", 0.7);

        List<ActorTrustScore> results = repo.findCapabilityDimensions(actorId, "review");
        assertThat(results).hasSize(2);
    }

    @Test
    void findAll_returnsEverything() {
        upsert("a1", ScoreType.GLOBAL, null, null, 0.5);
        upsert("a2", ScoreType.GLOBAL, null, null, 0.6);
        assertThat(repo.findAll()).hasSize(2);
    }

    @Test
    void findAllByLastComputedAtAfter_filters() {
        Instant threshold = Instant.parse("2026-06-01T00:00:00Z");
        upsertAt("a1", ScoreType.GLOBAL, null, null, 0.5, Instant.parse("2026-01-01T00:00:00Z"));
        upsertAt("a2", ScoreType.GLOBAL, null, null, 0.6, Instant.parse("2026-12-01T00:00:00Z"));

        List<ActorTrustScore> results = repo.findAllByLastComputedAtAfter(threshold);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).actorId).isEqualTo("a2");
    }

    @Test
    void findByActorIdAndScoreType_returnsMatchingRows() {
        String actorId = "actor-" + UUID.randomUUID();
        upsert(actorId, ScoreType.GLOBAL, null, null, 0.5);
        upsert(actorId, ScoreType.CAPABILITY, "review", null, 0.7);
        upsert(actorId, ScoreType.CAPABILITY, "code", null, 0.8);

        List<ActorTrustScore> results = repo.findByActorIdAndScoreType(actorId, ScoreType.CAPABILITY);
        assertThat(results).hasSize(2);
    }

    private void upsert(String actorId, ScoreType type, String cap, String dim, double score) {
        upsertAt(actorId, type, cap, dim, score, Instant.now());
    }

    private void upsertAt(String actorId, ScoreType type, String cap, String dim,
            double score, Instant at) {
        repo.upsert(actorId, type, cap, dim, ActorType.AGENT,
                score, 10, 1, 9.0, 2.0, 8, 2, at);
    }
}

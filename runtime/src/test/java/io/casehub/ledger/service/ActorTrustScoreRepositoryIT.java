package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.ActorTrustScore.ScoreType;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Integration tests for {@link ActorTrustScoreRepository} — covers all score types
 * and verifies backward compatibility of the GLOBAL score path.
 */
@QuarkusTest
@TestProfile(ActorTrustScoreRepositoryIT.RepoTestProfile.class)
class ActorTrustScoreRepositoryIT {

    public static class RepoTestProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "trust-repo-test";
        }
    }

    @Inject
    ActorTrustScoreRepository repo;

    // ── Backward compat: findByActorId still returns the GLOBAL score ─────────

    @Test
    @Transactional
    void findByActorId_returnsGlobalScore() {
        final String actorId = "actor-global-" + System.nanoTime();
        repo.upsert(actorId, ScoreType.GLOBAL, null, null, ActorType.AGENT,
                0.75, 5, 1, 2.0, 1.0, 4, 1, Instant.now());

        final var result = repo.findByActorId(actorId);

        assertThat(result).isPresent();
        assertThat(result.get().trustScore).isEqualTo(0.75);
        assertThat(result.get().scoreType).isEqualTo(ScoreType.GLOBAL);
        assertThat(result.get().capabilityKey).isNull();
        assertThat(result.get().dimensionKey).isNull();
    }

    @Test
    @Transactional
    void findByActorId_returnsEmpty_whenOnlyCapabilityRowExists() {
        final String actorId = "actor-cap-only-" + System.nanoTime();
        repo.upsert(actorId, ScoreType.CAPABILITY, "security-review", null, ActorType.AGENT,
                0.85, 3, 0, 2.5, 1.0, 3, 0, Instant.now());

        // findByActorId is scoped to GLOBAL — must not return the CAPABILITY row
        assertThat(repo.findByActorId(actorId)).isEmpty();
    }

    // ── New: upsert is idempotent — second upsert updates, not inserts ────────

    @Test
    @Transactional
    void upsert_global_isIdempotent() {
        final String actorId = "actor-idem-" + System.nanoTime();
        repo.upsert(actorId, ScoreType.GLOBAL, null, null, ActorType.AGENT,
                0.5, 1, 0, 1.5, 1.5, 1, 0, Instant.now());
        repo.upsert(actorId, ScoreType.GLOBAL, null, null, ActorType.AGENT,
                0.8, 10, 2, 3.0, 1.0, 9, 1, Instant.now());

        final var all = repo.findAll();
        final long count = all.stream()
                .filter(s -> s.actorId.equals(actorId) && s.scoreType == ScoreType.GLOBAL)
                .count();
        assertThat(count).isEqualTo(1);
        assertThat(repo.findByActorId(actorId).get().trustScore).isEqualTo(0.8);
    }

    // ── New: scoped score queries ─────────────────────────────────────────────

    @Test
    @Transactional
    void findCapabilityScore_returnsCapabilityScore() {
        final String actorId = "actor-scoped-" + System.nanoTime();
        repo.upsert(actorId, ScoreType.CAPABILITY, "security-review", null, ActorType.AGENT,
                0.85, 5, 0, 3.0, 1.0, 5, 0, Instant.now());

        final var result = repo.findCapabilityScore(actorId, "security-review");

        assertThat(result).isPresent();
        assertThat(result.get().trustScore).isEqualTo(0.85);
        assertThat(result.get().capabilityKey).isEqualTo("security-review");
    }

    @Test
    @Transactional
    void findCapabilityScore_returnsEmpty_whenKeyDiffers() {
        final String actorId = "actor-wrongkey-" + System.nanoTime();
        repo.upsert(actorId, ScoreType.CAPABILITY, "security-review", null, ActorType.AGENT,
                0.85, 5, 0, 3.0, 1.0, 5, 0, Instant.now());

        assertThat(repo.findCapabilityScore(actorId, "architecture-review")).isEmpty();
    }

    @Test
    @Transactional
    void findByActorIdAndScoreType_returnsAllCapabilityRows() {
        final String actorId = "actor-multi-" + System.nanoTime();
        repo.upsert(actorId, ScoreType.CAPABILITY, "security-review", null, ActorType.AGENT,
                0.85, 5, 0, 3.0, 1.0, 5, 0, Instant.now());
        repo.upsert(actorId, ScoreType.CAPABILITY, "architecture-review", null, ActorType.AGENT,
                0.60, 3, 1, 2.0, 1.5, 2, 1, Instant.now());

        final var results = repo.findByActorIdAndScoreType(actorId, ScoreType.CAPABILITY);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(s -> s.capabilityKey)
                .containsExactlyInAnyOrder("security-review", "architecture-review");
    }

    @Test
    @Transactional
    void upsert_capability_isIdempotent() {
        final String actorId = "actor-cap-idem-" + System.nanoTime();
        repo.upsert(actorId, ScoreType.CAPABILITY, "security-review", null, ActorType.AGENT,
                0.5, 1, 0, 1.5, 1.5, 1, 0, Instant.now());
        repo.upsert(actorId, ScoreType.CAPABILITY, "security-review", null, ActorType.AGENT,
                0.9, 10, 0, 5.0, 1.0, 10, 0, Instant.now());

        final var results = repo.findByActorIdAndScoreType(actorId, ScoreType.CAPABILITY);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).trustScore).isEqualTo(0.9);
    }

    // ── updateGlobalTrustScore still works ────────────────────────────────────

    @Test
    @Transactional
    void updateGlobalTrustScore_updatesExistingGlobalRow() {
        final String actorId = "actor-eigentrust-" + System.nanoTime();
        repo.upsert(actorId, ScoreType.GLOBAL, null, null, ActorType.AGENT,
                0.75, 5, 0, 2.0, 1.0, 5, 0, Instant.now());

        repo.updateGlobalTrustScore(actorId, 0.42);

        assertThat(repo.findByActorId(actorId).get().globalTrustScore).isEqualTo(0.42);
    }

    // ── DIMENSION rows ────────────────────────────────────────────────────────

    @Test
    @Transactional
    void upsert_dimension_storesDimensionRow() {
        final String actorId = "actor-dim-" + System.nanoTime();
        repo.upsert(actorId, ScoreType.DIMENSION, null, "thoroughness", ActorType.AGENT,
                0.78, 5, 0, 0.0, 0.0, 4, 1, Instant.now());

        final var result = repo.findDimensionScore(actorId, "thoroughness");

        assertThat(result).isPresent();
        assertThat(result.get().trustScore).isEqualTo(0.78);
        assertThat(result.get().scoreType).isEqualTo(ScoreType.DIMENSION);
        assertThat(result.get().dimensionKey).isEqualTo("thoroughness");
    }

    @Test
    @Transactional
    void upsert_dimension_isIdempotent() {
        final String actorId = "actor-dim-idem-" + System.nanoTime();
        repo.upsert(actorId, ScoreType.DIMENSION, null, "thoroughness", ActorType.AGENT,
                0.5, 2, 0, 0.0, 0.0, 2, 0, Instant.now());
        repo.upsert(actorId, ScoreType.DIMENSION, null, "thoroughness", ActorType.AGENT,
                0.9, 10, 0, 0.0, 0.0, 10, 0, Instant.now());

        final var results = repo.findByActorIdAndScoreType(actorId, ScoreType.DIMENSION);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).trustScore).isEqualTo(0.9);
    }

    @Test
    @Transactional
    void findByActorIdAndScoreType_dimension_returnsAllDimensionRows() {
        final String actorId = "actor-dim-multi-" + System.nanoTime();
        repo.upsert(actorId, ScoreType.DIMENSION, null, "thoroughness", ActorType.AGENT,
                0.8, 5, 0, 0.0, 0.0, 5, 0, Instant.now());
        repo.upsert(actorId, ScoreType.DIMENSION, null, "false-positive-rate", ActorType.AGENT,
                0.1, 3, 2, 0.0, 0.0, 1, 2, Instant.now());

        final var results = repo.findByActorIdAndScoreType(actorId, ScoreType.DIMENSION);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(s -> s.dimensionKey)
                .containsExactlyInAnyOrder("thoroughness", "false-positive-rate");
    }

    @Test
    @Transactional
    void findByActorId_global_notAffectedByDimensionRows() {
        final String actorId = "actor-dim-isolation-" + System.nanoTime();
        repo.upsert(actorId, ScoreType.GLOBAL, null, null, ActorType.AGENT,
                0.7, 5, 0, 2.5, 1.0, 5, 0, Instant.now());
        repo.upsert(actorId, ScoreType.DIMENSION, null, "thoroughness", ActorType.AGENT,
                0.9, 3, 0, 0.0, 0.0, 3, 0, Instant.now());

        final var global = repo.findByActorId(actorId);
        assertThat(global).isPresent();
        assertThat(global.get().scoreType).isEqualTo(ScoreType.GLOBAL);
        assertThat(global.get().trustScore).isEqualTo(0.7);
    }

    @Test
    @Transactional
    void findDimensionScore_wrongKey_returnsEmpty() {
        final String actorId = "actor-dim-wrongkey-" + System.nanoTime();
        repo.upsert(actorId, ScoreType.DIMENSION, null, "thoroughness", ActorType.AGENT,
                0.8, 5, 0, 0.0, 0.0, 5, 0, Instant.now());

        assertThat(repo.findDimensionScore(actorId, "false-positive-rate")).isEmpty();
    }

    // ── CAPABILITY_DIMENSION rows ──────────────────────────────────────────────

    @Test
    @Transactional
    void upsert_capabilityDimension_storesRow() {
        final String actorId = "actor-cd-" + System.nanoTime();
        repo.upsert(actorId, ScoreType.CAPABILITY_DIMENSION, "security-review", "thoroughness",
                ActorType.AGENT, 0.88, 5, 0, 0.0, 0.0, 4, 1, Instant.now());

        final var result = repo.findCapabilityDimension(actorId, "security-review", "thoroughness");

        assertThat(result).isPresent();
        assertThat(result.get().trustScore).isEqualTo(0.88);
        assertThat(result.get().scoreType).isEqualTo(ScoreType.CAPABILITY_DIMENSION);
        assertThat(result.get().capabilityKey).isEqualTo("security-review");
        assertThat(result.get().dimensionKey).isEqualTo("thoroughness");
    }

    @Test
    @Transactional
    void upsert_capabilityDimension_isIdempotent() {
        final String actorId = "actor-cd-idem-" + System.nanoTime();
        repo.upsert(actorId, ScoreType.CAPABILITY_DIMENSION, "security-review", "thoroughness",
                ActorType.AGENT, 0.5, 2, 0, 0.0, 0.0, 2, 0, Instant.now());
        repo.upsert(actorId, ScoreType.CAPABILITY_DIMENSION, "security-review", "thoroughness",
                ActorType.AGENT, 0.9, 10, 0, 0.0, 0.0, 10, 0, Instant.now());

        final var results = repo.findByActorIdAndScoreType(actorId, ScoreType.CAPABILITY_DIMENSION);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).trustScore).isEqualTo(0.9);
    }

    @Test
    @Transactional
    void findCapabilityDimension_returnsEmpty_whenKeyMismatches() {
        final String actorId = "actor-cd-mismatch-" + System.nanoTime();
        repo.upsert(actorId, ScoreType.CAPABILITY_DIMENSION, "security-review", "thoroughness",
                ActorType.AGENT, 0.8, 3, 0, 0.0, 0.0, 3, 0, Instant.now());

        assertThat(repo.findCapabilityDimension(actorId, "architecture-review", "thoroughness"))
                .isEmpty();
        assertThat(repo.findCapabilityDimension(actorId, "security-review", "false-positive-rate"))
                .isEmpty();
    }

    @Test
    @Transactional
    void findCapabilityDimensions_returnsAllForCapability() {
        final String actorId = "actor-cd-multi-" + System.nanoTime();
        repo.upsert(actorId, ScoreType.CAPABILITY_DIMENSION, "security-review", "thoroughness",
                ActorType.AGENT, 0.9, 5, 0, 0.0, 0.0, 5, 0, Instant.now());
        repo.upsert(actorId, ScoreType.CAPABILITY_DIMENSION, "security-review", "false-positive-rate",
                ActorType.AGENT, 0.1, 3, 2, 0.0, 0.0, 1, 2, Instant.now());
        repo.upsert(actorId, ScoreType.CAPABILITY_DIMENSION, "architecture-review", "thoroughness",
                ActorType.AGENT, 0.6, 4, 0, 0.0, 0.0, 4, 0, Instant.now());

        final var secResults = repo.findCapabilityDimensions(actorId, "security-review");
        assertThat(secResults).hasSize(2);
        assertThat(secResults).extracting(s -> s.dimensionKey)
                .containsExactlyInAnyOrder("thoroughness", "false-positive-rate");

        final var archResults = repo.findCapabilityDimensions(actorId, "architecture-review");
        assertThat(archResults).hasSize(1);
        assertThat(archResults.get(0).capabilityKey).isEqualTo("architecture-review");
    }

    @Test
    @Transactional
    void findByActorId_global_notAffectedByCapabilityDimensionRows() {
        final String actorId = "actor-cd-global-iso-" + System.nanoTime();
        repo.upsert(actorId, ScoreType.GLOBAL, null, null, ActorType.AGENT,
                0.7, 5, 0, 2.5, 1.0, 5, 0, Instant.now());
        repo.upsert(actorId, ScoreType.CAPABILITY_DIMENSION, "security-review", "thoroughness",
                ActorType.AGENT, 0.9, 3, 0, 0.0, 0.0, 3, 0, Instant.now());

        final var global = repo.findByActorId(actorId);
        assertThat(global).isPresent();
        assertThat(global.get().scoreType).isEqualTo(ScoreType.GLOBAL);
        assertThat(global.get().trustScore).isEqualTo(0.7);
    }
}

package io.casehub.ledger.service.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.ActorTrustScore.ScoreType;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.ledger.runtime.service.federation.ActorExport;
import io.casehub.ledger.runtime.service.federation.TrustExportService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(TrustExportServiceIT.ExportTestProfile.class)
class TrustExportServiceIT {

    public static class ExportTestProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "federation-export-test";
        }
    }

    @Inject TrustExportService exportService;
    @Inject ActorTrustScoreRepository trustRepo;

    // ── exportAll ─────────────────────────────────────────────────────────

    @Test
    @Transactional
    void exportAll_returnsActorsAtOrAboveThreshold() {
        final String high = "export-high-" + System.nanoTime();
        final String low  = "export-low-"  + System.nanoTime();
        final Instant now = Instant.now();

        trustRepo.upsert(high, ScoreType.GLOBAL, null, null, ActorType.AGENT,
                0.85, 10, 1, 8.0, 2.0, 9, 1, now);
        trustRepo.upsert(low, ScoreType.GLOBAL, null, null, ActorType.AGENT,
                0.30, 5, 3, 2.0, 3.0, 2, 3, now);

        final var payload = exportService.exportAll(0.5);

        assertThat(payload.actors()).extracting(a -> a.actorId()).contains(high);
        assertThat(payload.actors()).extracting(a -> a.actorId()).doesNotContain(low);
    }

    @Test
    @Transactional
    void exportAll_zeroThreshold_returnsAllActorsWithGlobalScore() {
        final String a = "export-zero-a-" + System.nanoTime();
        final String b = "export-zero-b-" + System.nanoTime();
        final Instant now = Instant.now();

        trustRepo.upsert(a, ScoreType.GLOBAL, null, null, ActorType.AGENT,
                0.1, 2, 1, 1.0, 2.0, 1, 1, now);
        trustRepo.upsert(b, ScoreType.GLOBAL, null, null, ActorType.HUMAN,
                0.9, 8, 0, 7.0, 1.0, 8, 0, now);

        final var payload = exportService.exportAll(0.0);

        assertThat(payload.actors()).extracting(a2 -> a2.actorId()).contains(a, b);
    }

    @Test
    @Transactional
    void exportAll_excludesActorsWithNoGlobalRow() {
        final String capOnly = "export-caponly-" + System.nanoTime();
        final Instant now = Instant.now();

        trustRepo.upsert(capOnly, ScoreType.CAPABILITY, "security-review", null, ActorType.AGENT,
                0.9, 5, 0, 4.0, 1.0, 5, 0, now);

        final var payload = exportService.exportAll(0.0);

        assertThat(payload.actors()).extracting(a -> a.actorId()).doesNotContain(capOnly);
    }

    // ── exportActor ───────────────────────────────────────────────────────

    @Test
    @Transactional
    void exportActor_returnsStructuredExportWithAllScoreTypes() {
        final String actorId = "export-actor-" + System.nanoTime();
        final Instant now = Instant.now();

        trustRepo.upsert(actorId, ScoreType.GLOBAL, null, null, ActorType.AGENT,
                0.80, 10, 1, 8.0, 2.0, 9, 1, now);
        trustRepo.upsert(actorId, ScoreType.CAPABILITY, "security-review", null, ActorType.AGENT,
                0.90, 6, 0, 5.0, 1.0, 6, 0, now);
        trustRepo.upsert(actorId, ScoreType.DIMENSION, null, "thoroughness", ActorType.AGENT,
                0.75, 6, 0, 0.0, 0.0, 5, 1, now);

        final var result = exportService.exportActor(actorId);

        assertThat(result).isPresent();
        final var export = result.get();
        assertThat(export.actors()).hasSize(1);

        final var actor = export.actors().get(0);
        assertThat(actor.actorId()).isEqualTo(actorId);
        assertThat(actor.actorType()).isEqualTo(ActorType.AGENT);

        assertThat(actor.globalScore()).isNotNull();
        assertThat(actor.globalScore().trustScore()).isEqualTo(0.80);
        assertThat(actor.globalScore().alpha()).isEqualTo(8.0);

        assertThat(actor.capabilityScores()).hasSize(1);
        assertThat(actor.capabilityScores().get(0).capabilityTag()).isEqualTo("security-review");
        assertThat(actor.capabilityScores().get(0).trustScore()).isEqualTo(0.90);

        assertThat(actor.dimensionScores()).hasSize(1);
        assertThat(actor.dimensionScores().get(0).dimension()).isEqualTo("thoroughness");
        assertThat(actor.dimensionScores().get(0).score()).isEqualTo(0.75);
        assertThat(actor.dimensionScores().get(0).sampleCount()).isEqualTo(6);
    }

    @Test
    @Transactional
    void exportActor_returnsEmpty_forUnknownActor() {
        assertThat(exportService.exportActor("no-such-actor-" + System.nanoTime())).isEmpty();
    }

    // ── exportDelta ───────────────────────────────────────────────────────

    @Test
    @Transactional
    void exportDelta_returnsActorsWithScoresChangedAfterSince() {
        final Instant since = Instant.now().minusSeconds(60);
        final String changed = "export-delta-new-"  + System.nanoTime();
        final String stable  = "export-delta-old-"  + System.nanoTime();

        trustRepo.upsert(stable, ScoreType.GLOBAL, null, null, ActorType.AGENT,
                0.7, 5, 0, 4.0, 1.0, 5, 0, since.minusSeconds(10));
        trustRepo.upsert(changed, ScoreType.GLOBAL, null, null, ActorType.AGENT,
                0.8, 8, 1, 6.0, 2.0, 7, 1, since.plusSeconds(10));

        final var payload = exportService.exportDelta(since);

        assertThat(payload.actors()).extracting(a -> a.actorId()).contains(changed);
        assertThat(payload.actors()).extracting(a -> a.actorId()).doesNotContain(stable);
    }

    @Test
    @Transactional
    void exportDelta_returnsEmptyActors_whenNoScoresChangedSince() {
        final Instant since = Instant.now().plusSeconds(60);
        final String actorId = "export-delta-none-" + System.nanoTime();
        final Instant now = Instant.now();

        trustRepo.upsert(actorId, ScoreType.GLOBAL, null, null, ActorType.AGENT,
                0.7, 5, 0, 4.0, 1.0, 5, 0, now);

        final var payload = exportService.exportDelta(since);

        assertThat(payload.actors()).isEmpty();
    }

    // ── payload metadata ──────────────────────────────────────────────────

    @Test
    @Transactional
    void exportAll_payloadContainsExportedAtTimestamp() {
        final Instant before = Instant.now().minusSeconds(1);
        final var payload = exportService.exportAll(0.0);
        assertThat(payload.exportedAt()).isAfter(before);
    }

    // ── capabilityDimensionScores ─────────────────────────────────────────

    @Test
    @Transactional
    void exportAll_includesCapabilityDimensionScores() {
        final String actorId = "agent-export-cd-" + UUID.randomUUID();
        final Instant now = Instant.now();

        // Seed a GLOBAL row (required for exportAll threshold check)
        trustRepo.upsert(actorId, ScoreType.GLOBAL, null, null,
                ActorType.AGENT, 0.8, 5, 0, 3.0, 1.0, 5, 0, now);
        // Seed a CAPABILITY_DIMENSION row
        trustRepo.upsert(actorId, ScoreType.CAPABILITY_DIMENSION,
                "security-review", "thoroughness",
                ActorType.AGENT, 0.9, 3, 0, 0.0, 0.0, 3, 0, now);

        final var payload = exportService.exportAll(0.0);

        final ActorExport actor = payload.actors().stream()
                .filter(a -> actorId.equals(a.actorId()))
                .findFirst().orElseThrow();
        assertThat(actor.capabilityDimensionScores()).hasSize(1);
        assertThat(actor.capabilityDimensionScores().get(0).capabilityTag())
                .isEqualTo("security-review");
        assertThat(actor.capabilityDimensionScores().get(0).dimension())
                .isEqualTo("thoroughness");
        assertThat(actor.capabilityDimensionScores().get(0).score())
                .isCloseTo(0.9, within(0.001));
    }
}

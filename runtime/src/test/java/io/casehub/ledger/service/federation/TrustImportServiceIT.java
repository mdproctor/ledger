package io.casehub.ledger.service.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.ActorTrustScore.ScoreType;
import io.casehub.ledger.api.model.ActorType;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.ledger.runtime.service.federation.ActorExport;
import io.casehub.ledger.runtime.service.federation.CapabilityDimensionScoreExport;
import io.casehub.ledger.runtime.service.federation.CapabilityScoreExport;
import io.casehub.ledger.runtime.service.federation.DimensionScoreExport;
import io.casehub.ledger.runtime.service.federation.GlobalScoreExport;
import io.casehub.ledger.runtime.service.federation.TrustExportPayload;
import io.casehub.ledger.runtime.service.federation.TrustImportService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(TrustImportServiceIT.ImportTestProfile.class)
class TrustImportServiceIT {

    public static class ImportTestProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "federation-import-test";
        }
    }

    @Inject TrustImportService importService;
    @Inject ActorTrustScoreRepository trustRepo;

    // ── seed-if-absent: new actor gets all rows ───────────────────────────

    @Test
    @Transactional
    void importTrust_seedsNewActor_allScoreTypes() {
        final String actorId = "import-new-" + System.nanoTime();
        final Instant ts = Instant.now();

        final var payload = payloadFor(actorId,
                new GlobalScoreExport(8.0, 2.0, 0.80, 10, 9, 1, ts),
                List.of(new CapabilityScoreExport("security-review", 5.0, 1.0, 0.83, 6, 6, 0, ts)),
                List.of(new DimensionScoreExport("thoroughness", 0.75, 5, ts)));

        importService.importTrust(payload);

        assertThat(trustRepo.findByActorId(actorId)).isPresent();
        assertThat(trustRepo.findByActorId(actorId).get().trustScore).isEqualTo(0.80);

        final var caps = trustRepo.findByActorIdAndScoreType(actorId, ScoreType.CAPABILITY);
        assertThat(caps).hasSize(1);
        assertThat(caps.get(0).capabilityKey).isEqualTo("security-review");

        final var dims = trustRepo.findByActorIdAndScoreType(actorId, ScoreType.DIMENSION);
        assertThat(dims).hasSize(1);
        assertThat(dims.get(0).dimensionKey).isEqualTo("thoroughness");
        assertThat(dims.get(0).trustScore).isEqualTo(0.75);
    }

    // ── seed-if-absent: existing actor is untouched ───────────────────────

    @Test
    @Transactional
    void importTrust_skipsExistingActor_whenGlobalRowPresent() {
        final String actorId = "import-existing-" + System.nanoTime();
        final Instant ts = Instant.now();

        trustRepo.upsert(actorId, ScoreType.GLOBAL, null, null, ActorType.AGENT,
                0.50, 3, 1, 2.0, 1.0, 2, 1, ts);

        final var payload = payloadFor(actorId,
                new GlobalScoreExport(8.0, 2.0, 0.80, 10, 9, 1, ts),
                List.of(), List.of());

        importService.importTrust(payload);

        assertThat(trustRepo.findByActorId(actorId).get().trustScore).isEqualTo(0.50);
    }

    // ── mixed payload: seeds new, skips existing ──────────────────────────

    @Test
    @Transactional
    void importTrust_mixedPayload_onlyNewActorSeeded() {
        final String existing = "import-mixed-old-" + System.nanoTime();
        final String fresh    = "import-mixed-new-" + System.nanoTime();
        final Instant ts = Instant.now();

        trustRepo.upsert(existing, ScoreType.GLOBAL, null, null, ActorType.AGENT,
                0.60, 5, 0, 3.0, 2.0, 5, 0, ts);

        final var existingExport = actorExport(existing,
                new GlobalScoreExport(9.0, 1.0, 0.90, 10, 10, 0, ts), List.of(), List.of());
        final var freshExport = actorExport(fresh,
                new GlobalScoreExport(7.0, 3.0, 0.70, 10, 7, 3, ts), List.of(), List.of());

        importService.importTrust(new TrustExportPayload(Instant.now(), "", List.of(existingExport, freshExport)));

        assertThat(trustRepo.findByActorId(existing).get().trustScore).isEqualTo(0.60);
        assertThat(trustRepo.findByActorId(fresh).get().trustScore).isEqualTo(0.70);
    }

    // ── no-op when payload is empty ───────────────────────────────────────

    @Test
    @Transactional
    void importTrust_emptyPayload_writesNothing() {
        final var payload = new TrustExportPayload(Instant.now(), "", List.of());
        importService.importTrust(payload);
        // no assertion needed beyond "no exception"
    }

    // ── capability_dimension seeding ──────────────────────────────────────

    @Test
    @Transactional
    void importTrust_seedsCapabilityDimensionScores() {
        final String actorId = "agent-import-cd-" + UUID.randomUUID();
        final Instant now = Instant.now();

        final CapabilityDimensionScoreExport cd = new CapabilityDimensionScoreExport(
                "security-review", "thoroughness", 0.88, 5, now);
        final ActorExport actor = new ActorExport(
                actorId, ActorType.AGENT,
                new GlobalScoreExport(2.0, 1.0, 0.67, 3, 3, 0, now),
                List.of(),
                List.of(),
                List.of(cd));
        final TrustExportPayload payload = new TrustExportPayload(now, "remote", List.of(actor));

        importService.importTrust(payload);

        final var row = trustRepo.findCapabilityDimension(actorId, "security-review", "thoroughness");
        assertThat(row).isPresent();
        assertThat(row.get().trustScore).isCloseTo(0.88, within(0.001));
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private TrustExportPayload payloadFor(final String actorId,
            final GlobalScoreExport global,
            final List<CapabilityScoreExport> caps,
            final List<DimensionScoreExport> dims) {
        return new TrustExportPayload(Instant.now(), "",
                List.of(actorExport(actorId, global, caps, dims)));
    }

    private ActorExport actorExport(final String actorId, final GlobalScoreExport global,
            final List<CapabilityScoreExport> caps, final List<DimensionScoreExport> dims) {
        return new ActorExport(actorId, ActorType.AGENT, global, caps, dims, List.of());
    }
}

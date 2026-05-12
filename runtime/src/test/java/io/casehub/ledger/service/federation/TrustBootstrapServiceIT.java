package io.casehub.ledger.service.federation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.ActorType;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.ledger.runtime.service.federation.ActorExport;
import io.casehub.ledger.runtime.service.federation.GlobalScoreExport;
import io.casehub.ledger.runtime.service.federation.TrustBootstrapService;
import io.casehub.ledger.runtime.service.federation.TrustBootstrapSource;
import io.casehub.ledger.runtime.service.federation.TrustExportPayload;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(TrustBootstrapServiceIT.BootstrapTestProfile.class)
class TrustBootstrapServiceIT {

    public static class BootstrapTestProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "federation-bootstrap-test";
        }
    }

    /** Inner @Alternative replaces NoOpTrustBootstrapSource in this test context. */
    @Alternative
    @ApplicationScoped
    static class CapturingBootstrapSource implements TrustBootstrapSource {
        static final List<String> QUERIED = new ArrayList<>();
        static TrustExportPayload RESPONSE = null;

        @Override
        public Optional<TrustExportPayload> fetchPriorTrust(final String actorId) {
            QUERIED.add(actorId);
            return Optional.ofNullable(RESPONSE);
        }
    }

    @BeforeEach
    void reset() {
        CapturingBootstrapSource.QUERIED.clear();
        CapturingBootstrapSource.RESPONSE = null;
    }

    @Inject TrustBootstrapService bootstrapService;
    @Inject ActorTrustScoreRepository trustRepo;

    // ── new actor + source returns payload → rows seeded ─────────────────

    @Test
    @Transactional
    void bootstrapIfNew_seedsNewActor_whenSourceReturnsPayload() {
        final String actorId = "bootstrap-new-" + System.nanoTime();
        final Instant ts = Instant.now();

        final var global = new GlobalScoreExport(7.0, 3.0, 0.70, 10, 7, 3, ts);
        final var actor = new ActorExport(actorId, ActorType.AGENT, global, List.of(), List.of());
        CapturingBootstrapSource.RESPONSE = new TrustExportPayload(ts, "", List.of(actor));

        bootstrapService.bootstrapIfNew(Set.of(actorId));

        assertThat(CapturingBootstrapSource.QUERIED).contains(actorId);
        assertThat(trustRepo.findByActorId(actorId)).isPresent();
        assertThat(trustRepo.findByActorId(actorId).get().trustScore).isEqualTo(0.70);
    }

    // ── new actor + source returns empty → no rows written ────────────────

    @Test
    @Transactional
    void bootstrapIfNew_writesNothing_whenSourceReturnsEmpty() {
        final String actorId = "bootstrap-empty-" + System.nanoTime();
        CapturingBootstrapSource.RESPONSE = null;

        bootstrapService.bootstrapIfNew(Set.of(actorId));

        assertThat(CapturingBootstrapSource.QUERIED).contains(actorId);
        assertThat(trustRepo.findByActorId(actorId)).isEmpty();
    }

    // ── empty set → source never called ──────────────────────────────────

    @Test
    void bootstrapIfNew_emptySet_doesNothing() {
        bootstrapService.bootstrapIfNew(Set.of());
        assertThat(CapturingBootstrapSource.QUERIED).isEmpty();
    }
}

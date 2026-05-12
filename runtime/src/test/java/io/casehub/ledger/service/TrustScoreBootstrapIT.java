package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.ActorType;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.TrustScoreJob;
import io.casehub.ledger.runtime.service.federation.ActorExport;
import io.casehub.ledger.runtime.service.federation.GlobalScoreExport;
import io.casehub.ledger.runtime.service.federation.TrustBootstrapSource;
import io.casehub.ledger.runtime.service.federation.TrustExportPayload;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(TrustScoreBootstrapIT.BootstrapJobProfile.class)
class TrustScoreBootstrapIT {

    public static class BootstrapJobProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "trust-score-bootstrap-test";
        }
    }

    @Alternative
    @ApplicationScoped
    static class SeedingBootstrapSource implements TrustBootstrapSource {
        static final List<String> QUERIED = new ArrayList<>();
        static double SEED_SCORE = 0.75;

        @Override
        public Optional<TrustExportPayload> fetchPriorTrust(final String actorId) {
            QUERIED.add(actorId);
            final Instant ts = Instant.now();
            final var global = new GlobalScoreExport(6.0, 2.0, SEED_SCORE, 8, 6, 2, ts);
            final var actor = new ActorExport(actorId, ActorType.AGENT, global, List.of(), List.of());
            return Optional.of(new TrustExportPayload(ts, "test-source", List.of(actor)));
        }
    }

    @BeforeEach
    void reset() {
        SeedingBootstrapSource.QUERIED.clear();
        SeedingBootstrapSource.SEED_SCORE = 0.75;
    }

    @Inject TrustScoreJob job;
    @Inject ActorTrustScoreRepository trustRepo;
    @Inject LedgerEntryRepository repo;
    @Inject EntityManager em;

    @Test
    @Transactional
    void runComputation_bootstrapEnabled_seedsNewActorBeforeComputation() {
        final String actorId = "bootstrap-job-" + UUID.randomUUID();

        // Write a ledger entry so the actor appears in byActor map
        LedgerTestFixtures.seedDecision(actorId, Instant.now(), null, repo, em);

        // Actor has no existing trust score — bootstrap should fire
        assertThat(trustRepo.findByActorId(actorId)).isEmpty();

        job.runComputation();

        // Bootstrap was queried
        assertThat(SeedingBootstrapSource.QUERIED).contains(actorId);

        // Score was written (computation ran after seeded row was written)
        assertThat(trustRepo.findByActorId(actorId)).isPresent();
    }

    @Test
    @Transactional
    void runComputation_bootstrapEnabled_existingActorNotQueried() {
        final String actorId = "bootstrap-existing-" + UUID.randomUUID();

        // Pre-seed a trust score so actor is "existing"
        trustRepo.upsert(actorId, io.casehub.ledger.api.model.ActorTrustScore.ScoreType.GLOBAL,
                null, ActorType.AGENT, 0.60, 5, 0, 3.0, 2.0, 5, 0, Instant.now());

        // Write a ledger entry so actor appears in byActor map
        LedgerTestFixtures.seedDecision(actorId, Instant.now(), null, repo, em);

        job.runComputation();

        // Existing actor was NOT queried for bootstrap
        assertThat(SeedingBootstrapSource.QUERIED).doesNotContain(actorId);
    }
}

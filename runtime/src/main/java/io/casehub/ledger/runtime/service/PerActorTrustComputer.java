package io.casehub.ledger.runtime.service;

import io.casehub.ledger.api.model.ActorTrustScoreBase;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import io.casehub.ledger.api.spi.TrustScoreSnapshotRepository;
import io.casehub.ledger.api.spi.AttestorCredibilityPolicy;
import io.casehub.ledger.core.trust.DecayFunction;
import io.casehub.ledger.core.trust.GlobalScoreStrategy;
import io.casehub.ledger.core.trust.PerActorTrustComputerCore;
import io.casehub.ledger.core.trust.TrustScoreCalculator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
class PerActorTrustComputer {

    private final PerActorTrustComputerCore core;

    @Inject
    PerActorTrustComputer(TrustScoreCalculator calculator,
                          ActorTrustScoreRepository trustRepo,
                          TrustScoreSnapshotRepository snapshotRepo) {
        this.core = new PerActorTrustComputerCore(calculator, trustRepo, snapshotRepo);
    }

    PerActorTrustComputer(DecayFunction decayFunction,
                          ActorTrustScoreRepository trustRepo,
                          TrustScoreSnapshotRepository snapshotRepo,
                          GlobalScoreStrategy globalScoreStrategy,
                          AttestorCredibilityPolicy credibilityPolicy) {
        this.core = new PerActorTrustComputerCore(
                new TrustScoreCalculator(decayFunction, globalScoreStrategy, credibilityPolicy),
                trustRepo, snapshotRepo);
    }

    List<ActorTrustScoreBase> computeForActor(String actorId,
                                              List<LedgerEntry> decisions,
                                              Map<UUID, List<LedgerAttestation>> attestationsByEntry,
                                              Instant now) {
        return core.computeForActor(actorId, decisions, attestationsByEntry, now);
    }
}

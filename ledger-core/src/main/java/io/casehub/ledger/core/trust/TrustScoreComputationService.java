package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.model.ActorTrustScoreBase;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.ScoreType;
import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import io.casehub.ledger.api.spi.CrossTenantLedgerEntryRepository;
import io.casehub.ledger.api.spi.TrustScoreSnapshotRepository;
import io.casehub.ledger.core.config.TrustScoreProperties;
import io.casehub.ledger.core.federation.TrustBootstrapServiceCore;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class TrustScoreComputationService {

    private final CrossTenantLedgerEntryRepository ledgerRepo;
    private final ActorTrustScoreRepository trustRepo;
    private final TrustScoreSnapshotRepository snapshotRepo;
    private final PerActorTrustComputerCore perActorComputer;
    private final TrustScorePublisherCore publisher;
    private final TrustScoreProperties properties;
    private final TrustBootstrapServiceCore bootstrapService;
    private final EigenTrustComputer eigenTrustComputer;

    public TrustScoreComputationService(CrossTenantLedgerEntryRepository ledgerRepo,
                                        ActorTrustScoreRepository trustRepo,
                                        TrustScoreSnapshotRepository snapshotRepo,
                                        PerActorTrustComputerCore perActorComputer,
                                        TrustScorePublisherCore publisher,
                                        TrustScoreProperties properties,
                                        TrustBootstrapServiceCore bootstrapService,
                                        EigenTrustComputer eigenTrustComputer) {
        this.ledgerRepo = ledgerRepo;
        this.trustRepo = trustRepo;
        this.snapshotRepo = snapshotRepo;
        this.perActorComputer = perActorComputer;
        this.publisher = publisher;
        this.properties = properties;
        this.bootstrapService = bootstrapService;
        this.eigenTrustComputer = eigenTrustComputer;
    }

    public void runComputation() {
        Map<String, ActorTrustScoreBase> previousSnapshot;
        if (publisher.needsPreviousSnapshot()) {
            previousSnapshot = trustRepo.findAllDetached().stream()
                    .filter(s -> s.scoreType == ScoreType.GLOBAL)
                    .collect(Collectors.toMap(s -> s.actorId, s -> s));
        } else {
            previousSnapshot = Map.of();
        }

        List<LedgerEntry> allEvents = ledgerRepo.findAllEvents();
        Map<String, List<LedgerEntry>> byActor = allEvents.stream()
                .filter(e -> e.actorId != null)
                .collect(Collectors.groupingBy(e -> e.actorId));

        if (properties.bootstrap().enabled()) {
            Set<String> existingActors = trustRepo.findAll().stream()
                    .map(s -> s.actorId)
                    .collect(Collectors.toSet());
            Set<String> newActors = new LinkedHashSet<>(byActor.keySet());
            newActors.removeAll(existingActors);
            if (!newActors.isEmpty()) {
                bootstrapService.bootstrapIfNew(newActors);
            }
        }

        Instant now = Instant.now();

        Set<UUID> entryIds = allEvents.stream()
                .map(e -> e.id)
                .collect(Collectors.toSet());
        @SuppressWarnings("unchecked")
        Map<UUID, List<LedgerAttestation>> attestationsByEntry =
                (Map<UUID, List<LedgerAttestation>>) (Map<?, ?>) ledgerRepo.findAttestationsForEntries(entryIds);

        for (Map.Entry<String, List<LedgerEntry>> actorEntry : byActor.entrySet()) {
            String actorId = actorEntry.getKey();
            List<LedgerEntry> decisions = actorEntry.getValue();

            Set<UUID> actorEntryIds = decisions.stream()
                    .map(e -> e.id).collect(Collectors.toSet());
            Map<UUID, List<LedgerAttestation>> actorAttestationsByEntry = new LinkedHashMap<>();
            for (UUID eid : actorEntryIds) {
                if (attestationsByEntry.containsKey(eid)) {
                    actorAttestationsByEntry.put(eid, attestationsByEntry.get(eid));
                }
            }

            perActorComputer.computeForActor(actorId, decisions, actorAttestationsByEntry, now);
        }

        if (properties.eigentrust().enabled()) {
            runEigenTrustPass(allEvents, attestationsByEntry);
        }

        List<ActorTrustScoreBase> currentScores = trustRepo.findAllDetached();
        publisher.publish(currentScores, previousSnapshot, now);

        int retentionDays = properties.snapshot().retentionDays();
        if (retentionDays > 0) {
            snapshotRepo.deleteOlderThan(now.minus(retentionDays, ChronoUnit.DAYS));
        }
    }

    private void runEigenTrustPass(List<LedgerEntry> allEvents,
                                   Map<UUID, List<LedgerAttestation>> attestationsByEntry) {
        Map<UUID, String> entryActorIndex = allEvents.stream()
                .filter(e -> e.actorId != null)
                .collect(Collectors.toMap(e -> e.id, e -> e.actorId));

        List<LedgerAttestation> allAttestations = attestationsByEntry.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        Set<String> preTrustedActors = properties.eigentrust().preTrustedActors()
                .map(LinkedHashSet::new)
                .orElseGet(LinkedHashSet::new);

        Map<String, Double> globalScores = eigenTrustComputer.compute(
                allAttestations, entryActorIndex, preTrustedActors);

        for (Map.Entry<String, Double> entry : globalScores.entrySet()) {
            trustRepo.updateGlobalTrustScore(entry.getKey(), entry.getValue());
        }
    }
}

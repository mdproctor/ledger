package io.casehub.ledger.core.federation;

import io.casehub.ledger.api.model.ActorTrustScoreBase;
import io.casehub.ledger.api.model.ScoreType;
import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import io.casehub.ledger.core.config.TrustScoreProperties;
import io.casehub.platform.api.identity.ActorType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class TrustExportServiceCore {

    private final ActorTrustScoreRepository trustRepo;
    private final TrustScoreProperties.ExportProperties exportProperties;

    public TrustExportServiceCore(ActorTrustScoreRepository trustRepo,
                                  TrustScoreProperties.ExportProperties exportProperties) {
        this.trustRepo = trustRepo;
        this.exportProperties = exportProperties;
    }

    public TrustExportPayload exportAll(double minTrustScore) {
        List<ActorTrustScoreBase> all = trustRepo.findAll();
        Set<String> qualifying = all.stream()
                .filter(s -> s.scoreType == ScoreType.GLOBAL && s.trustScore >= minTrustScore)
                .map(s -> s.actorId)
                .collect(Collectors.toSet());
        List<ActorTrustScoreBase> scores = all.stream()
                .filter(s -> qualifying.contains(s.actorId))
                .collect(Collectors.toList());
        return buildPayload(scores);
    }

    public Optional<TrustExportPayload> exportActor(String actorId) {
        List<ActorTrustScoreBase> scores = new ArrayList<>();
        scores.addAll(trustRepo.findByActorIdAndScoreType(actorId, ScoreType.GLOBAL));
        scores.addAll(trustRepo.findByActorIdAndScoreType(actorId, ScoreType.CAPABILITY));
        scores.addAll(trustRepo.findByActorIdAndScoreType(actorId, ScoreType.DIMENSION));
        scores.addAll(trustRepo.findByActorIdAndScoreType(actorId, ScoreType.CAPABILITY_DIMENSION));
        if (scores.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(buildPayload(scores));
    }

    public TrustExportPayload exportDelta(Instant since) {
        List<ActorTrustScoreBase> changed = trustRepo.findAllByLastComputedAtAfter(since);
        if (changed.isEmpty()) {
            return buildPayload(List.of());
        }
        Set<String> changedActors = changed.stream()
                .map(s -> s.actorId)
                .collect(Collectors.toSet());
        List<ActorTrustScoreBase> allForChanged = trustRepo.findAll().stream()
                .filter(s -> changedActors.contains(s.actorId))
                .collect(Collectors.toList());
        return buildPayload(allForChanged);
    }

    private TrustExportPayload buildPayload(List<ActorTrustScoreBase> scores) {
        Map<String, List<ActorTrustScoreBase>> byActor = scores.stream()
                .collect(Collectors.groupingBy(s -> s.actorId));
        List<ActorExport> actors = byActor.values().stream()
                .map(this::toActorExport)
                .collect(Collectors.toList());
        return new TrustExportPayload(
                Instant.now(),
                exportProperties.deploymentId().orElse(""),
                actors);
    }

    private ActorExport toActorExport(List<ActorTrustScoreBase> scores) {
        String actorId = scores.get(0).actorId;
        ActorType actorType = scores.stream()
                .map(s -> s.actorType)
                .filter(t -> t != null)
                .findFirst()
                .orElse(ActorType.HUMAN);

        GlobalScoreExport global = scores.stream()
                .filter(s -> s.scoreType == ScoreType.GLOBAL)
                .findFirst()
                .map(s -> new GlobalScoreExport(s.alphaValue, s.betaValue, s.trustScore,
                        s.decisionCount, s.attestationPositive, s.attestationNegative,
                        s.lastComputedAt))
                .orElse(null);

        List<CapabilityScoreExport> capabilities = scores.stream()
                .filter(s -> s.scoreType == ScoreType.CAPABILITY)
                .map(s -> new CapabilityScoreExport(s.capabilityKey, s.alphaValue, s.betaValue, s.trustScore,
                        s.decisionCount, s.attestationPositive, s.attestationNegative,
                        s.lastComputedAt))
                .collect(Collectors.toList());

        List<DimensionScoreExport> dimensions = scores.stream()
                .filter(s -> s.scoreType == ScoreType.DIMENSION)
                .map(s -> new DimensionScoreExport(s.dimensionKey, s.trustScore,
                        s.attestationPositive + s.attestationNegative, s.lastComputedAt))
                .collect(Collectors.toList());

        List<CapabilityDimensionScoreExport> capabilityDimensions = scores.stream()
                .filter(s -> s.scoreType == ScoreType.CAPABILITY_DIMENSION)
                .map(s -> new CapabilityDimensionScoreExport(s.capabilityKey, s.dimensionKey,
                        s.trustScore, s.attestationPositive + s.attestationNegative, s.lastComputedAt))
                .collect(Collectors.toList());

        return new ActorExport(actorId, actorType, global, capabilities, dimensions, capabilityDimensions);
    }
}

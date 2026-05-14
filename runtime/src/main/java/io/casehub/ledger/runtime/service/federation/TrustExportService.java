package io.casehub.ledger.runtime.service.federation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.casehub.ledger.api.model.ActorTrustScore.ScoreType;
import io.casehub.ledger.api.model.ActorType;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Structured read-model over {@link ActorTrustScore}.
 *
 * <p>
 * Consumed by upper layers (dashboard, compliance reports) and future cross-deployment
 * trust federation. See design spec 2026-05-12-trust-federation-bootstrap-design.md.
 */
@ApplicationScoped
public class TrustExportService {

    @Inject
    ActorTrustScoreRepository trustRepo;

    @Inject
    LedgerConfig config;

    /**
     * Export all actors whose GLOBAL trust score meets or exceeds {@code minTrustScore}.
     * Actors with no GLOBAL row are excluded regardless of threshold.
     */
    public TrustExportPayload exportAll(final double minTrustScore) {
        final List<ActorTrustScore> all = trustRepo.findAll();
        final Set<String> qualifying = all.stream()
                .filter(s -> s.scoreType == ScoreType.GLOBAL && s.trustScore >= minTrustScore)
                .map(s -> s.actorId)
                .collect(Collectors.toSet());
        final List<ActorTrustScore> scores = all.stream()
                .filter(s -> qualifying.contains(s.actorId))
                .collect(Collectors.toList());
        return buildPayload(scores);
    }

    /**
     * Export a single actor's complete trust profile.
     *
     * @return empty if the actor has no computed trust scores
     */
    public Optional<TrustExportPayload> exportActor(final String actorId) {
        final List<ActorTrustScore> scores = new ArrayList<>();
        scores.addAll(trustRepo.findByActorIdAndScoreType(actorId, ScoreType.GLOBAL));
        scores.addAll(trustRepo.findByActorIdAndScoreType(actorId, ScoreType.CAPABILITY));
        scores.addAll(trustRepo.findByActorIdAndScoreType(actorId, ScoreType.DIMENSION));
        scores.addAll(trustRepo.findByActorIdAndScoreType(actorId, ScoreType.CAPABILITY_DIMENSION));
        if (scores.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(buildPayload(scores));
    }

    /**
     * Export complete profiles for all actors with any score change after {@code since}.
     * Returns an empty actors list if no scores have changed.
     */
    public TrustExportPayload exportDelta(final Instant since) {
        final List<ActorTrustScore> changed = trustRepo.findAllByLastComputedAtAfter(since);
        if (changed.isEmpty()) {
            return buildPayload(List.of());
        }
        final Set<String> changedActors = changed.stream()
                .map(s -> s.actorId)
                .collect(Collectors.toSet());
        final List<ActorTrustScore> allForChanged = trustRepo.findAll().stream()
                .filter(s -> changedActors.contains(s.actorId))
                .collect(Collectors.toList());
        return buildPayload(allForChanged);
    }

    private TrustExportPayload buildPayload(final List<ActorTrustScore> scores) {
        final Map<String, List<ActorTrustScore>> byActor = scores.stream()
                .collect(Collectors.groupingBy(s -> s.actorId));
        final List<ActorExport> actors = byActor.values().stream()
                .map(this::toActorExport)
                .collect(Collectors.toList());
        return new TrustExportPayload(
                Instant.now(),
                config.trustScore().export().deploymentId().orElse(""),
                actors);
    }

    private ActorExport toActorExport(final List<ActorTrustScore> scores) {
        final String actorId = scores.get(0).actorId;
        final ActorType actorType = scores.stream()
                .map(s -> s.actorType)
                .filter(t -> t != null)
                .findFirst()
                .orElse(ActorType.HUMAN);

        final GlobalScoreExport global = scores.stream()
                .filter(s -> s.scoreType == ScoreType.GLOBAL)
                .findFirst()
                .map(s -> new GlobalScoreExport(s.alpha, s.beta, s.trustScore,
                        s.decisionCount, s.attestationPositive, s.attestationNegative,
                        s.lastComputedAt))
                .orElse(null);

        final List<CapabilityScoreExport> capabilities = scores.stream()
                .filter(s -> s.scoreType == ScoreType.CAPABILITY)
                .map(s -> new CapabilityScoreExport(s.capabilityKey, s.alpha, s.beta, s.trustScore,
                        s.decisionCount, s.attestationPositive, s.attestationNegative,
                        s.lastComputedAt))
                .collect(Collectors.toList());

        final List<DimensionScoreExport> dimensions = scores.stream()
                .filter(s -> s.scoreType == ScoreType.DIMENSION)
                .map(s -> new DimensionScoreExport(s.dimensionKey, s.trustScore,
                        s.attestationPositive + s.attestationNegative, s.lastComputedAt))
                .collect(Collectors.toList());

        final List<CapabilityDimensionScoreExport> capabilityDimensions = scores.stream()
                .filter(s -> s.scoreType == ScoreType.CAPABILITY_DIMENSION)
                .map(s -> new CapabilityDimensionScoreExport(s.capabilityKey, s.dimensionKey,
                        s.trustScore, s.attestationPositive + s.attestationNegative, s.lastComputedAt))
                .collect(Collectors.toList());

        return new ActorExport(actorId, actorType, global, capabilities, dimensions, capabilityDimensions);
    }
}

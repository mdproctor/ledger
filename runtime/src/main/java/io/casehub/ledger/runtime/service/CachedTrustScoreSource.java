package io.casehub.ledger.runtime.service;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import io.casehub.ledger.api.model.ActorTrustScore.ScoreType;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.runtime.qualifier.CrossTenant;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.ledger.runtime.service.routing.TrustScoreActorUpdatedEvent;
import io.casehub.ledger.runtime.service.routing.TrustScoreFullPayload;

/**
 * Cached {@link TrustScoreSource}: in-memory maps hydrated at startup and refreshed
 * by CDI events from the batch job and incremental observer.
 *
 * <p>Separate maps per score type avoid key format ambiguity and match the query patterns
 * of {@code TrustCandidateClassifier} (capability + decisionCount per candidate).
 *
 * <p>Observes both {@link TrustScoreFullPayload} (batch refresh) and
 * {@link TrustScoreActorUpdatedEvent} (incremental refresh). The incremental observer
 * was missing from the previous engine-side {@code TrustScoreCache} — this fixes the
 * staleness bug where incremental updates wrote to DB but the cache never saw them.
 *
 * <p>Activate via
 * {@code quarkus.arc.selected-alternatives=io.casehub.ledger.runtime.service.CachedTrustScoreSource}.
 */
@ApplicationScoped
@Alternative
public class CachedTrustScoreSource implements TrustScoreSource {

    record CachedCapabilityScore(double trustScore, int decisionCount) {}

    private final ConcurrentHashMap<String, Double> globalScores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedCapabilityScore> capabilityScores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> dimensionScores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> capDimScores = new ConcurrentHashMap<>();

    private final ActorTrustScoreRepository repository;

    @Inject
    public CachedTrustScoreSource(@CrossTenant final ActorTrustScoreRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void hydrate() {
        repository.findAll().forEach(this::index);
    }

    public void onFull(@Observes final TrustScoreFullPayload payload) {
        globalScores.clear();
        capabilityScores.clear();
        dimensionScores.clear();
        capDimScores.clear();
        payload.scores().forEach(this::index);
    }

    public void onActorUpdated(@Observes final TrustScoreActorUpdatedEvent event) {
        event.scores().forEach(this::index);
    }

    private void index(final ActorTrustScore s) {
        switch (s.scoreType) {
            case GLOBAL -> globalScores.put(s.actorId, s.trustScore);
            case CAPABILITY -> {
                if (s.capabilityKey != null) {
                    capabilityScores.put(s.actorId + "\0" + s.capabilityKey,
                            new CachedCapabilityScore(s.trustScore, s.decisionCount));
                }
            }
            case DIMENSION -> {
                if (s.dimensionKey != null) {
                    dimensionScores.put(s.actorId + "\0" + s.dimensionKey, s.trustScore);
                }
            }
            case CAPABILITY_DIMENSION -> {
                if (s.capabilityKey != null && s.dimensionKey != null) {
                    capDimScores.put(s.actorId + "\0" + s.capabilityKey + "\0" + s.dimensionKey,
                            s.trustScore);
                }
            }
        }
    }

    @Override
    public OptionalDouble globalScore(final String actorId) {
        final Double v = globalScores.get(actorId);
        return v != null ? OptionalDouble.of(v) : OptionalDouble.empty();
    }

    @Override
    public OptionalDouble capabilityScore(final String actorId, final String capabilityTag) {
        final CachedCapabilityScore s = capabilityScores.get(actorId + "\0" + capabilityTag);
        return s != null ? OptionalDouble.of(s.trustScore()) : OptionalDouble.empty();
    }

    @Override
    public OptionalDouble dimensionScore(final String actorId, final String dimensionKey) {
        final Double v = dimensionScores.get(actorId + "\0" + dimensionKey);
        return v != null ? OptionalDouble.of(v) : OptionalDouble.empty();
    }

    @Override
    public OptionalDouble capabilityDimensionScore(final String actorId, final String capabilityTag,
            final String dimensionKey) {
        final Double v = capDimScores.get(actorId + "\0" + capabilityTag + "\0" + dimensionKey);
        return v != null ? OptionalDouble.of(v) : OptionalDouble.empty();
    }

    @Override
    public int decisionCount(final String actorId, final String capabilityTag) {
        final CachedCapabilityScore s = capabilityScores.get(actorId + "\0" + capabilityTag);
        return s != null ? s.decisionCount() : 0;
    }

    @Override
    public Map<String, Double> allCapabilityScores(final String actorId) {
        final String prefix = actorId + "\0";
        return capabilityScores.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .collect(Collectors.toMap(
                        e -> e.getKey().substring(prefix.length()),
                        e -> e.getValue().trustScore()));
    }

    @Override
    public Map<String, Double> allDimensionScores(final String actorId) {
        final String prefix = actorId + "\0";
        return dimensionScores.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .collect(Collectors.toMap(
                        e -> e.getKey().substring(prefix.length()),
                        Map.Entry::getValue));
    }

    @Override
    public Map<String, Double> qualityScores(final String actorId, final String capabilityTag) {
        final String prefix = actorId + "\0" + capabilityTag + "\0";
        return capDimScores.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .collect(Collectors.toMap(
                        e -> e.getKey().substring(prefix.length()),
                        Map.Entry::getValue));
    }
}

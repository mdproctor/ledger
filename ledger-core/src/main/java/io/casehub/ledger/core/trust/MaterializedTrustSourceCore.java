package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.model.ActorTrustScoreBase;
import io.casehub.ledger.api.model.ScoreType;
import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import io.casehub.ledger.api.spi.TrustScoreSource;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

public class MaterializedTrustSourceCore implements TrustScoreSource {

    private final ActorTrustScoreRepository repository;

    public MaterializedTrustSourceCore(ActorTrustScoreRepository repository) {
        this.repository = repository;
    }

    @Override
    public OptionalDouble globalScore(String actorId) {
        return repository.findByActorId(actorId)
                .map(s -> OptionalDouble.of(s.trustScore))
                .orElse(OptionalDouble.empty());
    }

    @Override
    public OptionalDouble capabilityScore(String actorId, String capabilityTag) {
        return repository.findCapabilityScore(actorId, capabilityTag)
                .map(s -> OptionalDouble.of(s.trustScore))
                .orElse(OptionalDouble.empty());
    }

    @Override
    public OptionalDouble dimensionScore(String actorId, String dimensionKey) {
        return repository.findDimensionScore(actorId, dimensionKey)
                .map(s -> OptionalDouble.of(s.trustScore))
                .orElse(OptionalDouble.empty());
    }

    @Override
    public OptionalDouble capabilityDimensionScore(String actorId, String capabilityTag,
                                                   String dimensionKey) {
        return repository.findCapabilityDimension(actorId, capabilityTag, dimensionKey)
                .map(s -> OptionalDouble.of(s.trustScore))
                .orElse(OptionalDouble.empty());
    }

    @Override
    public int decisionCount(String actorId, String capabilityTag) {
        return repository.findCapabilityScore(actorId, capabilityTag)
                .map(s -> s.decisionCount)
                .orElse(0);
    }

    @Override
    public Map<String, Double> allCapabilityScores(String actorId) {
        return repository.findByActorIdAndScoreType(actorId, ScoreType.CAPABILITY).stream()
                .collect(Collectors.toMap(s -> s.capabilityKey, s -> s.trustScore));
    }

    @Override
    public Map<String, Double> allDimensionScores(String actorId) {
        return repository.findByActorIdAndScoreType(actorId, ScoreType.DIMENSION).stream()
                .collect(Collectors.toMap(s -> s.dimensionKey, s -> s.trustScore));
    }

    @Override
    public Map<String, Double> qualityScores(String actorId, String capabilityTag) {
        return repository.findCapabilityDimensions(actorId, capabilityTag).stream()
                .collect(Collectors.toMap(s -> s.dimensionKey, s -> s.trustScore));
    }

    @Override
    public Map<String, OptionalDouble> scoresFor(List<String> candidateIds, String capabilityTag) {
        if (candidateIds.isEmpty()) {
            return Map.of();
        }
        Map<String, OptionalDouble> result = new LinkedHashMap<>(candidateIds.size());
        candidateIds.forEach(id -> result.put(id, OptionalDouble.empty()));
        repository.findCapabilityScoresByActorIds(candidateIds, capabilityTag)
                .forEach(s -> result.put(s.actorId, OptionalDouble.of(s.trustScore)));
        return result;
    }

    @Override
    public Map<String, Integer> decisionCountsFor(List<String> candidateIds, String capabilityTag) {
        if (candidateIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> result = new LinkedHashMap<>(candidateIds.size());
        candidateIds.forEach(id -> result.put(id, 0));
        repository.findCapabilityScoresByActorIds(candidateIds, capabilityTag)
                .forEach(s -> result.put(s.actorId, s.decisionCount));
        return result;
    }
}

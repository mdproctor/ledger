package io.casehub.ledger.runtime.service;

import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

@ApplicationScoped
@DefaultBean
public class MaterializedTrustScoreSource implements TrustScoreSource {

    private final io.casehub.ledger.core.trust.MaterializedTrustSourceCore core;

    @Inject
    public MaterializedTrustScoreSource(ActorTrustScoreRepository repository) {
        this.core = new io.casehub.ledger.core.trust.MaterializedTrustSourceCore(repository);
    }

    @Override
    public OptionalDouble globalScore(String actorId) {return core.globalScore(actorId);}

    @Override
    public OptionalDouble capabilityScore(String actorId, String capabilityTag) {return core.capabilityScore(actorId, capabilityTag);}

    @Override
    public OptionalDouble dimensionScore(String actorId, String dimensionKey) {return core.dimensionScore(actorId, dimensionKey);}

    @Override
    public OptionalDouble capabilityDimensionScore(String actorId, String capabilityTag, String dimensionKey) {return core.capabilityDimensionScore(actorId, capabilityTag, dimensionKey);}

    @Override
    public int decisionCount(String actorId, String capabilityTag) {return core.decisionCount(actorId, capabilityTag);}

    @Override
    public Map<String, Double> allCapabilityScores(String actorId) {return core.allCapabilityScores(actorId);}

    @Override
    public Map<String, Double> allDimensionScores(String actorId) {return core.allDimensionScores(actorId);}

    @Override
    public Map<String, Double> qualityScores(String actorId, String capabilityTag) {return core.qualityScores(actorId, capabilityTag);}

    @Override
    public Map<String, OptionalDouble> scoresFor(List<String> candidateIds, String capabilityTag) {return core.scoresFor(candidateIds, capabilityTag);}

    @Override
    public Map<String, Integer> decisionCountsFor(List<String> candidateIds, String capabilityTag) {return core.decisionCountsFor(candidateIds, capabilityTag);}
}

package io.casehub.ledger.runtime.service.federation;

import java.util.List;

import io.casehub.ledger.api.model.ActorType;

/** All trust scores for a single actor, structured by score type. */
public record ActorExport(
        String actorId,
        ActorType actorType,
        GlobalScoreExport globalScore,
        List<CapabilityScoreExport> capabilityScores,
        List<DimensionScoreExport> dimensionScores,
        List<CapabilityDimensionScoreExport> capabilityDimensionScores) {
}

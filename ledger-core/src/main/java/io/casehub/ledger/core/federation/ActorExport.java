package io.casehub.ledger.core.federation;

import java.util.List;

import io.casehub.platform.api.identity.ActorType;

public record ActorExport(
        String actorId,
        ActorType actorType,
        GlobalScoreExport globalScore,
        List<CapabilityScoreExport> capabilityScores,
        List<DimensionScoreExport> dimensionScores,
        List<CapabilityDimensionScoreExport> capabilityDimensionScores) {
}

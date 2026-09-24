package io.casehub.ledger.core.api;

import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.api.view.CapabilityScoreView;
import io.casehub.ledger.api.view.TrustRoutingProfileView;
import io.casehub.ledger.api.view.TrustScoreView;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;

@McpDomain(value = "ledger/trust", basePath = "/api/v1/ledger/trust")
public class LedgerTrustApiCore {

    private final TrustScoreSource trustScoreSource;

    public LedgerTrustApiCore(TrustScoreSource trustScoreSource) {
        this.trustScoreSource = trustScoreSource;
    }

    @PlatformQuery("Global trust score for an actor — aggregate across all capabilities")
    public TrustScoreView trustScore(@PathParam String actorId) {
        return new TrustScoreView(
                actorId,
                trustScoreSource.globalScore(actorId),
                trustScoreSource.allCapabilityScores(actorId),
                trustScoreSource.allDimensionScores(actorId));
    }

    @PlatformQuery("Capability-scoped trust score with quality dimensions")
    public CapabilityScoreView capabilityScore(@PathParam String actorId,
                                                @PathParam String capabilityTag) {
        return new CapabilityScoreView(
                actorId, capabilityTag,
                trustScoreSource.capabilityScore(actorId, capabilityTag),
                trustScoreSource.decisionCount(actorId, capabilityTag),
                trustScoreSource.qualityScores(actorId, capabilityTag));
    }

    @PlatformQuery("Composite trust routing profile — global + capability in one call")
    public TrustRoutingProfileView routingProfile(@PathParam String actorId,
                                                    @PathParam String capabilityTag) {
        return new TrustRoutingProfileView(
                actorId, capabilityTag,
                trustScoreSource.globalScore(actorId),
                trustScoreSource.capabilityScore(actorId, capabilityTag),
                trustScoreSource.decisionCount(actorId, capabilityTag),
                trustScoreSource.qualityScores(actorId, capabilityTag));
    }
}

package io.casehub.ledger.rest.api;

import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.api.view.CapabilityScoreView;
import io.casehub.ledger.api.view.TrustRoutingProfileView;
import io.casehub.ledger.api.view.TrustScoreView;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@McpDomain(value = "ledger/trust", app = "ledger", basePath = "/api/v1/ledger/trust", summary = "Trust score queries — global, capability, and dimension scores")
@ApplicationScoped
public class DefaultLedgerTrustApi {

    private final io.casehub.ledger.core.api.LedgerTrustApiCore core;

    @Inject
    DefaultLedgerTrustApi(TrustScoreSource trustScoreSource) {
        this.core = new io.casehub.ledger.core.api.LedgerTrustApiCore(trustScoreSource);
    }

    @PlatformQuery("Global trust score for an actor — aggregate across all capabilities")
    public TrustScoreView trustScore(@PathParam String actorId) {
        return core.trustScore(actorId);
    }

    @PlatformQuery("Capability-scoped trust score with quality dimensions")
    public CapabilityScoreView capabilityScore(@PathParam String actorId, @PathParam String capabilityTag) {
        return core.capabilityScore(actorId, capabilityTag);
    }

    @PlatformQuery("Composite trust routing profile — global + capability in one call")
    public TrustRoutingProfileView routingProfile(@PathParam String actorId, @PathParam String capabilityTag) {
        return core.routingProfile(actorId, capabilityTag);
    }
}

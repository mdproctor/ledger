package io.casehub.ledger.graphql;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.ModelEnricher;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@McpDomain(value = "ledger/model", app = "ledger", summary = "Ledger domain model enrichment and state")
@ApplicationScoped
public class LedgerModelEnricher implements ModelEnricher {

    @Override
    public String summary() {
        return "Immutable audit ledger — append entries, record attestations, "
                + "query trust scores (global, capability, routing profile), "
                + "and verify Merkle tree integrity. "
                + "Supports composite trustRoutingProfile for single-call trust resolution.";
    }

    @Override
    public Map<String, Object> state() {
        return Map.of();
    }
}

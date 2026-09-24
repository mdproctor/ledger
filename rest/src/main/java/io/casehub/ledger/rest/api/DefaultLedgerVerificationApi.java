package io.casehub.ledger.rest.api;

import io.casehub.ledger.api.view.InclusionProofView;
import io.casehub.ledger.api.view.VerificationView;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@McpDomain(value = "ledger/verification", basePath = "/api/v1/ledger")
@ApplicationScoped
public class DefaultLedgerVerificationApi {

    private final io.casehub.ledger.core.api.LedgerVerificationApiCore core;

    @Inject
    DefaultLedgerVerificationApi(io.casehub.ledger.api.spi.LedgerEntryRepository ledgerRepo,
                                 io.casehub.ledger.api.spi.LedgerMerkleFrontierRepository frontierRepo) {
        var verificationCore = new io.casehub.ledger.core.service.VerificationServiceCore(ledgerRepo, frontierRepo);
        this.core = new io.casehub.ledger.core.api.LedgerVerificationApiCore(verificationCore);
    }

    @PlatformQuery("Verify Merkle tree integrity for all entries of a subject")
    public VerificationView verify(UUID subjectId, @ContextParam("tenancyId") String tenancyId) {
        return core.verify(subjectId, tenancyId);
    }

    @PlatformQuery("Get Merkle inclusion proof for a single entry")
    public InclusionProofView inclusionProof(@PathParam UUID entryId, @ContextParam("tenancyId") String tenancyId) {
        return core.inclusionProof(entryId, tenancyId);
    }
}

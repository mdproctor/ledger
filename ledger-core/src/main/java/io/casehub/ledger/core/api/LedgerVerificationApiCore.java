package io.casehub.ledger.core.api;

import io.casehub.ledger.api.view.InclusionProofView;
import io.casehub.ledger.api.view.VerificationView;
import io.casehub.ledger.core.merkle.InclusionProof;
import io.casehub.ledger.core.service.VerificationServiceCore;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;

import java.util.UUID;

@McpDomain(value = "ledger/verification", basePath = "/api/v1/ledger")
public class LedgerVerificationApiCore {

    private final VerificationServiceCore verificationService;

    public LedgerVerificationApiCore(VerificationServiceCore verificationService) {
        this.verificationService = verificationService;
    }

    @PlatformQuery("Verify Merkle tree integrity for all entries of a subject")
    public VerificationView verify(UUID subjectId, @ContextParam("tenancyId") String tenancyId) {
        String tid = LedgerEntryApiCore.defaultTenancyId(tenancyId);
        boolean verified = verificationService.verify(subjectId, tid);
        String treeRoot = verified ? verificationService.treeRoot(subjectId, tid) : null;
        return new VerificationView(subjectId, treeRoot, verified);
    }

    @PlatformQuery("Get Merkle inclusion proof for a single entry")
    public InclusionProofView inclusionProof(@PathParam UUID entryId,
                                              @ContextParam("tenancyId") String tenancyId) {
        String tid = LedgerEntryApiCore.defaultTenancyId(tenancyId);
        InclusionProof proof = verificationService.inclusionProof(entryId, tid);
        var steps = proof.siblings().stream()
                .map(s -> new InclusionProofView.ProofStepView(s.hash(), s.side().name()))
                .toList();
        return new InclusionProofView(
                proof.entryId(), proof.entryIndex(), proof.treeSize(),
                proof.leafHash(), steps, proof.treeRoot());
    }
}

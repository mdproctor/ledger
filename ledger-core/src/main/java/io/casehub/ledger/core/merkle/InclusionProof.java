package io.casehub.ledger.core.merkle;

import java.util.List;
import java.util.UUID;

public record InclusionProof(
        UUID entryId,
        int entryIndex,
        int treeSize,
        String leafHash,
        List<ProofStep> siblings,
        String treeRoot) {
}

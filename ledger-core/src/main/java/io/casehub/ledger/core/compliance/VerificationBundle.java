package io.casehub.ledger.core.compliance;

import java.time.Instant;
import java.util.List;

public record VerificationBundle(
        String tenancyId,
        Instant generatedAt,
        List<SubjectChain> chains,
        String verificationScript,
        String verificationInstructions) {
}

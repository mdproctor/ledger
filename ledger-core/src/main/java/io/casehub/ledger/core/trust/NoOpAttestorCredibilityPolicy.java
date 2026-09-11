package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.spi.AttestorCredibilityPolicy;

public class NoOpAttestorCredibilityPolicy implements AttestorCredibilityPolicy {

    @Override
    public CredibilityAssessment assess(String attestorId) {
        return CredibilityAssessment.NEUTRAL;
    }
}

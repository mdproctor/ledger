package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.model.AttestationVerdict;

@FunctionalInterface
public interface DecayFunction {

    double weight(long ageInDays, AttestationVerdict verdict);
}

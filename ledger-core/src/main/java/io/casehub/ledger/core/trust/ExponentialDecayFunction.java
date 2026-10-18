package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.model.AttestationVerdict;

/**
 * Default decay function: exponential decay {@code 2^(-ageInDays / halfLifeDays)} with an
 * asymmetric valence multiplier. FLAGGED/CHALLENGED attestations use a multiplier < 1.0
 * (configurable via {@code casehub.ledger.decay.flagged-persistence-multiplier}), causing
 * them to decay slower — they persist longer as negative evidence in the trust model.
 */
public class ExponentialDecayFunction implements DecayFunction {

    private final int halfLifeDays;
    private final double flaggedPersistenceMultiplier;

    public ExponentialDecayFunction(final int halfLifeDays, final double flaggedPersistenceMultiplier) {
        this.halfLifeDays = halfLifeDays;
        this.flaggedPersistenceMultiplier = flaggedPersistenceMultiplier;
    }

    @Override
    public double weight(final long ageInDays, final AttestationVerdict verdict) {
        final double effectiveHalfLife = isNegative(verdict)
                ? halfLifeDays / flaggedPersistenceMultiplier
                : halfLifeDays;
        return Math.pow(2.0, -(double) ageInDays / effectiveHalfLife);
    }

    private static boolean isNegative(final AttestationVerdict verdict) {
        return verdict == AttestationVerdict.FLAGGED || verdict == AttestationVerdict.CHALLENGED;
    }
}

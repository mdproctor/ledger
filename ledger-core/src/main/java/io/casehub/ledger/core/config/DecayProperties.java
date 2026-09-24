package io.casehub.ledger.core.config;

public record DecayProperties(double flaggedPersistenceMultiplier) {
    public static DecayProperties defaults() {
        return new DecayProperties(0.5);
    }
}

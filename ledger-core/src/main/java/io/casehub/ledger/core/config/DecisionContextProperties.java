package io.casehub.ledger.core.config;

public record DecisionContextProperties(boolean enabled) {
    public static DecisionContextProperties defaults() {
        return new DecisionContextProperties(true);
    }
}

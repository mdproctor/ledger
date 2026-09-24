package io.casehub.ledger.core.config;

public record HashChainProperties(boolean enabled) {
    public static HashChainProperties defaults() {
        return new HashChainProperties(true);
    }
}

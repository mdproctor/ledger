package io.casehub.ledger.core.config;

public record ErasureReceiptProperties(boolean enabled) {
    public static ErasureReceiptProperties defaults() {
        return new ErasureReceiptProperties(false);
    }
}

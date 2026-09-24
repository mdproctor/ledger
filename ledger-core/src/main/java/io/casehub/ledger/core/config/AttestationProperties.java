package io.casehub.ledger.core.config;

public record AttestationProperties(boolean enabled) {
    public static AttestationProperties defaults() {
        return new AttestationProperties(true);
    }
}

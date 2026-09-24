package io.casehub.ledger.core.config;

public record EvidenceProperties(boolean enabled) {
    public static EvidenceProperties defaults() {
        return new EvidenceProperties(false);
    }
}

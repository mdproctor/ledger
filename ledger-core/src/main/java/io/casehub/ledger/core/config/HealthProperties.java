package io.casehub.ledger.core.config;

public record HealthProperties(boolean enabled, String checkInterval) {
    public static HealthProperties defaults() {
        return new HealthProperties(true, "1h");
    }
}

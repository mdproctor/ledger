package io.casehub.ledger.core.config;

public record RetentionProperties(boolean enabled, int operationalDays, boolean archiveBeforeDelete) {
    public static RetentionProperties defaults() {
        return new RetentionProperties(false, 180, true);
    }
}

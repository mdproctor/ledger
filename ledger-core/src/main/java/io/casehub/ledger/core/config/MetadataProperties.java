package io.casehub.ledger.core.config;

public record MetadataProperties(int maxSize) {
    public static MetadataProperties defaults() {
        return new MetadataProperties(65536);
    }
}

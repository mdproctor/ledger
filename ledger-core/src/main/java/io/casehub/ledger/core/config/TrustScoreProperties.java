package io.casehub.ledger.core.config;

import io.casehub.ledger.core.trust.AttestationAggregator;

import java.util.List;
import java.util.Optional;

public record TrustScoreProperties(
        boolean enabled,
        int decayHalfLifeDays,
        boolean routingEnabled,
        double routingDeltaThreshold,
        String schedule,
        EigenTrustProperties eigentrust,
        ExportProperties export,
        BootstrapProperties bootstrap,
        MaterializationProperties materialization,
        IncrementalProperties incremental,
        SnapshotProperties snapshot,
        AttestationAggregator.Strategy aggregationStrategy
) {

    public record EigenTrustProperties(boolean enabled, double alpha, Optional<List<String>> preTrustedActors) {
        public static EigenTrustProperties defaults() {
            return new EigenTrustProperties(false, 0.15, Optional.empty());
        }
    }

    public record ExportProperties(Optional<String> deploymentId) {
        public static ExportProperties defaults() {
            return new ExportProperties(Optional.empty());
        }
    }

    public record BootstrapProperties(boolean enabled) {
        public static BootstrapProperties defaults() {
            return new BootstrapProperties(false);
        }
    }

    public record MaterializationProperties(boolean enabled) {
        public static MaterializationProperties defaults() {
            return new MaterializationProperties(true);
        }
    }

    public record IncrementalProperties(boolean enabled) {
        public static IncrementalProperties defaults() {
            return new IncrementalProperties(false);
        }
    }

    public record SnapshotProperties(int retentionDays) {
        public static SnapshotProperties defaults() {
            return new SnapshotProperties(365);
        }
    }

    public static TrustScoreProperties defaults() {
        return new TrustScoreProperties(
                false, 90, false, 0.01, "24h",
                EigenTrustProperties.defaults(),
                ExportProperties.defaults(),
                BootstrapProperties.defaults(),
                MaterializationProperties.defaults(),
                IncrementalProperties.defaults(),
                SnapshotProperties.defaults(),
                AttestationAggregator.Strategy.WEIGHTED_MAJORITY
        );
    }
}

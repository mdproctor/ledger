package io.casehub.ledger.deployment;

import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.flyway.runtime.FlywayBuildTimeConfig;

import io.casehub.ledger.runtime.service.ReactiveAgentSignatureVerificationService;
import io.casehub.ledger.runtime.service.ReactiveKeyRotationService;

import org.jboss.logging.Logger;

/**
 * Quarkus build-time processor for the Ledger extension.
 *
 * <p>
 * Registers the "ledger" feature, gates the reactive service tier behind
 * {@code casehub.ledger.reactive.enabled=true}, and validates that consumers
 * have configured {@code classpath:db/ledger/migration} in their Flyway locations.
 */
class LedgerProcessor {

    private static final Logger LOG = Logger.getLogger(LedgerProcessor.class);
    private static final String FEATURE = "ledger";
    private static final String LEDGER_MIGRATION_PATH = "db/ledger/migration";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Excludes reactive-tier service beans from CDI when
     * {@code casehub.ledger.reactive.enabled} is not {@code true}.
     *
     * <p>
     * This is a build-time decision — reactive beans are absent from the CDI graph
     * in JDBC-only consumers, preventing unsatisfied-dependency failures at
     * augmentation time.
     */
    @BuildStep
    void excludeReactiveBeans(
            final LedgerBuildTimeConfig config,
            final BuildProducer<ExcludedTypeBuildItem> excluded) {
        if (!config.reactive().enabled()) {
            excluded.produce(
                    new ExcludedTypeBuildItem(ReactiveKeyRotationService.class.getName()));
            excluded.produce(
                    new ExcludedTypeBuildItem(ReactiveAgentSignatureVerificationService.class.getName()));
        }
    }

    /**
     * Warns at build time if no Flyway datasource is configured with
     * {@code classpath:db/ledger/migration}. Without this location, ledger tables
     * will not be created — a misconfiguration that surfaces only at runtime.
     *
     * <p>Emits a warning rather than failing the build so that misconfigured
     * environments surface at runtime with a clear Hibernate schema error rather
     * than a hard build gate. DDL-generation test environments will also see this
     * warning; they can safely ignore it.
     */
    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void validateFlywayMigrationLocation(final FlywayBuildTimeConfig flywayBuildTimeConfig) {
        final boolean hasLedgerLocation = flywayBuildTimeConfig.datasources().values().stream()
                .flatMap(ds -> ds.locations().stream())
                .map(loc -> loc.replace("classpath:", "").strip())
                .anyMatch(loc -> loc.equals(LEDGER_MIGRATION_PATH)
                        || loc.endsWith("/" + LEDGER_MIGRATION_PATH));

        if (!hasLedgerLocation) {
            LOG.warn("""
                    casehub-ledger is on the classpath but classpath:db/ledger/migration is not \
                    configured in any Flyway datasource locations. \
                    Ledger tables will not be created. \
                    Add classpath:db/ledger/migration to quarkus.flyway.locations \
                    (or quarkus.flyway.<named-datasource>.locations when using a named datasource).\
                    """);
        }
    }
}

package io.casehub.ledger.deployment;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Build-time configuration for the Ledger extension.
 *
 * <p>
 * Properties declared here are read during Quarkus augmentation and are not
 * present in the runtime configuration. Changes require a rebuild.
 */
@ConfigMapping(prefix = "casehub.ledger")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface LedgerBuildTimeConfig {

    /** Reactive tier configuration. */
    ReactiveConfig reactive();

    interface ReactiveConfig {
        /**
         * Whether to activate the reactive service tier
         * ({@link io.casehub.ledger.runtime.service.ReactiveKeyRotationService},
         * {@link io.casehub.ledger.runtime.service.ReactiveAgentSignatureVerificationService}).
         *
         * <p>
         * Set to {@code true} in deployments that provide a reactive datasource.
         * JDBC-only consumers must leave this unset (defaults to {@code false}).
         * Requires a {@code ReactiveLedgerEntryRepository} implementation on the classpath.
         */
        @WithDefault("false")
        boolean enabled();
    }
}

package io.casehub.ledger.deployment;

import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

import io.casehub.ledger.runtime.service.ReactiveLedgerVerificationService;
import io.casehub.ledger.runtime.service.ReactiveKeyRotationService;

/**
 * Quarkus build-time processor for the Ledger extension.
 *
 * <p>
 * Registers the "ledger" feature and gates the reactive service tier behind
 * {@code casehub.ledger.reactive.enabled=true}. When the property is absent or
 * {@code false}, reactive-tier beans are excluded from CDI augmentation so that
 * JDBC-only consumers build cleanly.
 */
class LedgerProcessor {

    private static final String FEATURE = "ledger";

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
                    new ExcludedTypeBuildItem(ReactiveLedgerVerificationService.class.getName()));
        }
    }
}

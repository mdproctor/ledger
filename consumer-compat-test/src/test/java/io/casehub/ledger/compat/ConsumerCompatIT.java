package io.casehub.ledger.compat;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Boots casehub-ledger with no persistence infrastructure and no quarkus.arc.exclude-types.
 *
 * <p>If this test compiles and passes, every CDI injection point in the ledger runtime is
 * satisfied by a {@code @DefaultBean} no-op — no consumer needs to add exclude-types to work
 * around unsatisfied dependencies.
 *
 * <p>A failing build here means a new ledger bean introduced an unsatisfied injection point
 * that will break consumers who don't have the relevant infrastructure on their classpath.
 */
@QuarkusTest
class ConsumerCompatIT {

    @Test
    void ledgerBootsWithoutInfrastructure() {
        // Quarkus ARC validates the full CDI graph at build time.
        // Reaching this line means every injection point was satisfied.
    }
}

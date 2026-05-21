package io.casehub.ledger;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@code classpath:db/ledger/migration} as the canonical Flyway location for
 * casehub-ledger. Independent of {@code application.properties} — any drift between
 * the actual file path and the documented contract fails here immediately.
 *
 * <p>This test runs without Quarkus: plain Flyway + H2, no CDI container.
 */
class FlywayLocationContractTest {

    @Test
    void ledgerMigrations_atCanonicalPath_allExecuteSuccessfully() {
        final Flyway flyway = Flyway.configure()
                .dataSource("jdbc:h2:mem:ledger-contract;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/ledger/migration")
                .load();

        final MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted)
                .as("expected all 8 ledger base migrations (V1000-V1007)")
                .isEqualTo(8);
    }
}

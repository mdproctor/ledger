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

    @Test
    void legacyPath_dbMigration_containsNoLedgerMigrations() {
        // Catches stale build artifacts: if db/migration/ is present in the JAR
        // (e.g. from a mvn install without clean after the path move), Flyway
        // scanning classpath:db/migration would find ledger V1000+ alongside
        // casehub-work V1-V27 — reproducing the exact conflict this move was
        // intended to fix.
        final Flyway flyway = Flyway.configure()
                .dataSource("jdbc:h2:mem:ledger-legacy-absent;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration")
                .load();

        final MigrateResult result = flyway.migrate();

        assertThat(result.migrationsExecuted)
                .as("classpath:db/migration must contain no ledger migrations — run mvn clean install if this fails")
                .isEqualTo(0);
    }
}

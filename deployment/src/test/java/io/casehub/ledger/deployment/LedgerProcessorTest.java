package io.casehub.ledger.deployment;

import io.quarkus.deployment.builditem.nativeimage.NativeImageResourcePatternsBuildItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerProcessorTest {

    private final LedgerProcessor processor = new LedgerProcessor();

    @Test
    void migrationSqlFiles_areRegisteredForNativeInclusion() {
        final NativeImageResourcePatternsBuildItem item = processor.registerMigrationResources();

        assertThat(item.getIncludePatterns())
                .hasSize(1)
                .allSatisfy(p -> assertThat("db/ledger/migration/V1000__ledger_base_schema.sql").matches(p));
    }
}

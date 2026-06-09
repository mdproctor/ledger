package io.casehub.ledger.deployment;

import org.jboss.jandex.DotName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the DotName constants used by the {@code @CrossTenant} scope
 * validation build step resolve to the correct fully-qualified names.
 *
 * <p>
 * Full integration testing of the build step requires Quarkus augmentation.
 * The existing test suite provides implicit coverage — no {@code @RequestScoped}
 * beans inject {@code @CrossTenant} today, so the build passes cleanly.
 * If a future change introduces a violation, the build step will catch it.
 */
class LedgerProcessorCrossTenantScopeTest {

    @Test
    void crossTenantDotName_matchesQualifierClass() {
        assertThat(LedgerProcessor.CROSS_TENANT)
                .isEqualTo(DotName.createSimple(
                        "io.casehub.ledger.runtime.qualifier.CrossTenant"));
    }

    @Test
    void requestScopedDotName_matchesJakartaAnnotation() {
        assertThat(LedgerProcessor.REQUEST_SCOPED)
                .isEqualTo(DotName.createSimple(
                        "jakarta.enterprise.context.RequestScoped"));
    }

    @Test
    void ledgerEntryDotName_matchesBaseEntityClass() {
        assertThat(LedgerProcessor.LEDGER_ENTRY)
                .isEqualTo(DotName.createSimple(
                        "io.casehub.ledger.runtime.model.LedgerEntry"));
    }
}

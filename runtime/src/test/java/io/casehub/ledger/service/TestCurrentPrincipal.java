package io.casehub.ledger.service;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import io.quarkus.arc.DefaultBean;

/**
 * Test-only {@link CurrentPrincipal} that returns {@link TenancyConstants#DEFAULT_TENANT_ID}.
 *
 * <p>Registered as {@link DefaultBean} so that CDI injection points requiring
 * {@code CurrentPrincipal} (e.g. {@code DefaultOutcomeRecorder}) resolve correctly
 * in the H2/JDBC test suite.
 */
@DefaultBean
@ApplicationScoped
class TestCurrentPrincipal implements CurrentPrincipal {

    @Override
    public String actorId() {
        return "test-principal";
    }

    @Override
    public Set<String> groups() {
        return Set.of();
    }

    @Override
    public String tenancyId() {
        return TenancyConstants.DEFAULT_TENANT_ID;
    }

    @Override
    public boolean isCrossTenantAdmin() {
        return false;
    }
}

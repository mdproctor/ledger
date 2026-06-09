package io.casehub.ledger.runtime.service.identity;

import io.casehub.ledger.runtime.qualifier.LedgerSystem;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/**
 * Ledger-internal system-actor CurrentPrincipal. Always isCrossTenantAdmin().
 *
 * <p>Not @DefaultBean — accessed only via @LedgerSystem qualifier from CrossTenantProducer.
 *
 * <p>Interim: delete when casehub-platform ships a platform-level system-actor principal
 * with isCrossTenantAdmin() = true.
 */
@ApplicationScoped
@LedgerSystem
public class LedgerSystemCurrentPrincipal implements CurrentPrincipal {

    @Override
    public String actorId() {
        return "system";
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
        return true;
    }
}

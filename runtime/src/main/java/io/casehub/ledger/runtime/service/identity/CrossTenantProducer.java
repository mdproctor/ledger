package io.casehub.ledger.runtime.service.identity;

import io.casehub.ledger.runtime.qualifier.CrossTenant;
import io.casehub.ledger.runtime.qualifier.LedgerSystem;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.ledger.runtime.repository.ActorIdentityBindingRepository;
import io.casehub.ledger.runtime.repository.CrossTenantLedgerEntryRepository;
import io.casehub.ledger.runtime.repository.KeyRotationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Produces @CrossTenant-qualified cross-tenant repository beans.
 *
 * <p>The @LedgerSystem LedgerSystemCurrentPrincipal check is a contract assertion:
 * if isCrossTenantAdmin() ever returns false, this producer fails at startup
 * rather than silently granting access.
 */
@ApplicationScoped
public class CrossTenantProducer {

    @Inject @LedgerSystem LedgerSystemCurrentPrincipal systemPrincipal;
    @Inject CrossTenantLedgerEntryRepository ledgerRepo;
    @Inject ActorTrustScoreRepository trustRepo;
    @Inject KeyRotationRepository keyRotationRepo;
    @Inject ActorIdentityBindingRepository identityBindingRepo;

    @Produces
    @CrossTenant
    @ApplicationScoped
    public CrossTenantLedgerEntryRepository produceLedgerRepo() {
        assertCrossTenant();
        return ledgerRepo;
    }

    @Produces
    @CrossTenant
    @ApplicationScoped
    public ActorTrustScoreRepository produceTrustRepo() {
        assertCrossTenant();
        return trustRepo;
    }

    @Produces
    @CrossTenant
    @ApplicationScoped
    public KeyRotationRepository produceKeyRotationRepo() {
        assertCrossTenant();
        return keyRotationRepo;
    }

    @Produces
    @CrossTenant
    @ApplicationScoped
    public ActorIdentityBindingRepository produceIdentityBindingRepo() {
        assertCrossTenant();
        return identityBindingRepo;
    }

    private void assertCrossTenant() {
        if (!systemPrincipal.isCrossTenantAdmin()) {
            throw new IllegalStateException(
                    "LedgerSystemCurrentPrincipal.isCrossTenantAdmin() must return true — ledger#127");
        }
    }
}

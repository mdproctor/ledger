package io.casehub.ledger.runtime.qualifier;

import jakarta.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CDI qualifier for cross-tenant data access where a tenant-scoped variant exists.
 *
 * <p>Applied to implementations of {@link io.casehub.ledger.runtime.repository.CrossTenantLedgerEntryRepository}
 * and its reactive counterpart. The qualifier disambiguates between the tenant-scoped
 * {@link io.casehub.ledger.runtime.repository.LedgerEntryRepository} and the cross-tenant variant.
 * Unqualified injection of {@code CrossTenantLedgerEntryRepository} fails at startup —
 * the qualifier is mandatory.
 *
 * <p>Not applied to inherently cross-tenant repos ({@code ActorTrustScoreRepository},
 * {@code KeyRotationRepository}, {@code ActorIdentityBindingRepository}) — these have
 * no tenant-scoped variant, and the type itself enforces the cross-tenant boundary.
 *
 * <p>Build-time enforcement: {@code @RequestScoped} beans injecting
 * {@code @CrossTenant} produce a deployment error via {@code LedgerProcessor}.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER})
public @interface CrossTenant {}

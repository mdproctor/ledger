package io.casehub.ledger.runtime.qualifier;

import jakarta.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the system-level {@link io.casehub.platform.api.identity.CurrentPrincipal}
 * for ledger-internal use. Analogous to engine's {@code @EngineSystem}.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER})
public @interface LedgerSystem {}

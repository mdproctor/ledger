package io.casehub.ledger.runtime.service.identity;

import io.casehub.platform.api.identity.IdentityVerificationResult;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.Unremovable;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Ledger adapter wrapping the platform {@link io.casehub.platform.identity.ReactiveAgentIdentityVerificationService}.
 *
 * <p>Extracts fields from a {@link LedgerEntry} and delegates to the domain-agnostic platform service.
 * {@code @DefaultBean @Unremovable}: always active, no injection point within the extension.
 */
@DefaultBean
@ApplicationScoped
@Unremovable
public class ReactiveAgentIdentityVerificationService {

    @Inject
    io.casehub.platform.identity.ReactiveAgentIdentityVerificationService delegate;

    public Uni<IdentityVerificationResult> verifyIdentityBindingAsync(final LedgerEntry entry) {
        return delegate.verifyIdentityBindingAsync(entry.actorId, entry.actorDid, entry.agentPublicKey);
    }
}

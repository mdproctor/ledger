package io.casehub.ledger.runtime.service.identity;

import io.casehub.platform.api.identity.IdentityVerificationResult;
import io.casehub.ledger.runtime.model.LedgerEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Ledger adapter wrapping the platform {@link io.casehub.platform.identity.AgentIdentityVerificationService}.
 *
 * <p>Extracts the relevant fields ({@code actorId}, {@code actorDid}, {@code agentPublicKey})
 * from a {@link LedgerEntry} and delegates to the domain-agnostic platform service.
 * Existing consumers that inject by ledger type continue to work unchanged.
 */
@ApplicationScoped
public class AgentIdentityVerificationService {

    private final io.casehub.platform.identity.AgentIdentityVerificationService delegate;

    @Inject
    public AgentIdentityVerificationService(
            final io.casehub.platform.identity.AgentIdentityVerificationService delegate) {
        this.delegate = delegate;
    }

    public IdentityVerificationResult verifyIdentityBinding(final LedgerEntry entry) {
        return delegate.verifyIdentityBinding(entry.actorId, entry.actorDid, entry.agentPublicKey);
    }
}

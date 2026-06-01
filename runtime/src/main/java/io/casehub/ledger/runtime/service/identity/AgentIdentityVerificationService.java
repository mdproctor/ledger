package io.casehub.ledger.runtime.service.identity;

import io.casehub.platform.api.identity.IdentityVerificationResult;
import io.casehub.platform.api.identity.DIDResolver;
import io.casehub.ledger.runtime.model.LedgerEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Arrays;

/**
 * Read-path DID identity verification service.
 *
 * <p>Verifies that the stored {@link LedgerEntry#agentPublicKey} matches a verification
 * method in the current DID document for the stored {@link LedgerEntry#actorDid}.
 * Also confirms that the DID document's {@code alsoKnownAs} claim includes {@code actorId}.
 *
 * <p><strong>VC validation is not re-run on the read path.</strong> VC results are
 * stored at write time in {@code ActorIdentityBindingEntry}. Consumers needing
 * write-time VC validation results should query
 * {@code ActorIdentityBindingRepository.latestBindingFor(actorId)}.
 *
 * <p>Uses the injected {@link DIDResolver} with its configured TTL cache.
 */
@ApplicationScoped
public class AgentIdentityVerificationService {

    private final DIDResolver resolver;

    @Inject
    public AgentIdentityVerificationService(final DIDResolver resolver) {
        this.resolver = resolver;
    }

    public IdentityVerificationResult verifyIdentityBinding(final LedgerEntry entry) {
        if (entry.actorDid == null) return IdentityVerificationResult.UNVERIFIABLE;
        if (entry.agentPublicKey == null) return IdentityVerificationResult.UNSIGNED;

        final var docOpt = resolver.resolve(entry.actorDid);
        if (docOpt.isEmpty()) return IdentityVerificationResult.DID_UNRESOLVABLE;

        final var doc = docOpt.get();
        if (!doc.alsoKnownAs().contains(entry.actorId)) {
            return IdentityVerificationResult.IDENTITY_MISMATCH;
        }

        final boolean keyMatch = doc.verificationMethods().stream()
            .anyMatch(vm -> Arrays.equals(vm.publicKeyBytes(), entry.agentPublicKey));
        return keyMatch ? IdentityVerificationResult.VALID : IdentityVerificationResult.KEY_MISMATCH;
    }
}

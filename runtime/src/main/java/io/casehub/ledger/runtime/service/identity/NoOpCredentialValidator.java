package io.casehub.ledger.runtime.service.identity;

import io.casehub.ledger.api.model.CredentialValidationResult;
import io.casehub.ledger.api.spi.identity.AgentCredentialValidator;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

/** Default no-op — DID document key check is sufficient. VC validation is opt-in. */
@ApplicationScoped
@DefaultBean
public class NoOpCredentialValidator implements AgentCredentialValidator {
    @Override
    public Optional<CredentialValidationResult> validate(final String actorId, final String did) {
        return Optional.empty();
    }
}

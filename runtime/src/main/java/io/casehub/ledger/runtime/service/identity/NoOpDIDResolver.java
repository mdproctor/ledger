package io.casehub.ledger.runtime.service.identity;

import io.casehub.ledger.api.spi.identity.DIDDocument;
import io.casehub.ledger.api.spi.resolve.DIDResolver;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

/** Default no-op implementation. Resolves nothing — DID verification is skipped. */
@ApplicationScoped
@DefaultBean
public class NoOpDIDResolver implements DIDResolver {
    @Override
    public Optional<DIDDocument> resolve(final String did) {
        return Optional.empty();
    }
}

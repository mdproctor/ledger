package io.casehub.ledger.runtime.service.federation;

import java.util.Optional;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

/** Default no-op — trust bootstrapping is opt-in. Provide a custom {@link TrustBootstrapSource} to activate. */
@DefaultBean
@ApplicationScoped
public class NoOpTrustBootstrapSource implements TrustBootstrapSource {

    @Override
    public Optional<TrustExportPayload> fetchPriorTrust(final String actorId) {
        return Optional.empty();
    }
}

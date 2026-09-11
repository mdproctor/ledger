package io.casehub.ledger.core.federation;

import java.util.Optional;

/** Default no-op — trust bootstrapping is opt-in. Provide a custom {@link TrustBootstrapSource} to activate. */
public class NoOpTrustBootstrapSource implements TrustBootstrapSource {

    @Override
    public Optional<TrustExportPayload> fetchPriorTrust(final String actorId) {
        return Optional.empty();
    }
}

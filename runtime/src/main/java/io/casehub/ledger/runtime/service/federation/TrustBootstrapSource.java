package io.casehub.ledger.runtime.service.federation;

import java.util.Optional;

/**
 * SPI for fetching prior trust data for an actor from an external source.
 *
 * <p>
 * Called by {@link TrustBootstrapService} the first time an actor appears in this deployment.
 * Implementations may call a remote deployment, query a shared registry, or any other source.
 *
 * <p>
 * Default: {@link NoOpTrustBootstrapSource} — returns empty, preserving Beta(1,1) uninformative prior.
 */
public interface TrustBootstrapSource {

    /**
     * Fetch prior trust data for the given actor, if available.
     *
     * @param actorId the actor identifier to look up
     * @return a payload containing the actor's prior scores, or empty if none available
     */
    Optional<TrustExportPayload> fetchPriorTrust(String actorId);
}

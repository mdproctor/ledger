package io.casehub.ledger.api.spi.identity;

import java.util.Optional;

/**
 * Maps an actorId string to its DID URI.
 *
 * <p>
 * Return empty for actors without a DID binding.
 *
 * <p>
 * Implementations are CDI beans. The default no-op implementation returns
 * {@link Optional#empty()} for every actor. Override with {@code @Alternative}
 * to integrate with an identity registry.
 */
public interface ActorDIDProvider {

    /**
     * Returns the DID URI for the given actor, or empty if the actor has no DID binding.
     *
     * @param actorId the ledger actor identifier (e.g. {@code "claude:tarkus-reviewer@v1"})
     * @return the DID URI (e.g. {@code "did:web:example.com:agents:tarkus"}), or empty
     */
    Optional<String> didFor(String actorId);
}

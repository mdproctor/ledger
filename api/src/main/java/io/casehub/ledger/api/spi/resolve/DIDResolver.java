package io.casehub.ledger.api.spi.resolve;

import io.casehub.ledger.api.spi.identity.DIDDocument;

import java.util.Optional;

/**
 * Resolves a DID URI to a DID document.
 *
 * <p>
 * Return empty when the DID is unresolvable — for example when the method is
 * unsupported, the network is unreachable, or the document does not exist.
 *
 * <p>
 * Implementations are CDI beans. Multiple resolvers may be registered; the
 * resolution pipeline selects the first non-empty result by DID method prefix.
 */
public interface DIDResolver {

    /**
     * Resolves the given DID URI to its document.
     *
     * @param did the DID URI to resolve (e.g. {@code "did:web:example.com:agents:tarkus"})
     * @return the resolved document, or empty when unresolvable
     */
    Optional<DIDDocument> resolve(String did);
}

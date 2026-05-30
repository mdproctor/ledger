package io.casehub.ledger.service.identity;

import io.casehub.ledger.api.spi.identity.DIDDocument;
import io.casehub.ledger.api.spi.resolve.DIDResolver;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Map-backed DIDResolver for tests. Supports arbitrary DIDDocuments including alsoKnownAs.
 * Use this as the primary test helper — KeyDIDResolver cannot set alsoKnownAs.
 * No CDI annotations — use directly or as a CDI @Alternative in @QuarkusTest.
 */
public class TestDIDResolver implements DIDResolver {

    private final Map<String, DIDDocument> docs = new HashMap<>();

    public TestDIDResolver register(final String did, final DIDDocument doc) {
        docs.put(did, doc);
        return this;
    }

    @Override
    public Optional<DIDDocument> resolve(final String did) {
        return Optional.ofNullable(docs.get(did));
    }
}

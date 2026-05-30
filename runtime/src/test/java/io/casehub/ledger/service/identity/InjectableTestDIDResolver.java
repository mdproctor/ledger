package io.casehub.ledger.service.identity;

import io.casehub.ledger.api.spi.identity.DIDDocument;
import io.casehub.ledger.api.spi.resolve.DIDResolver;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CDI-injectable {@link DIDResolver} for {@code @QuarkusTest} integration tests.
 *
 * <p>Replaces {@code NoOpDIDResolver} (which is {@code @DefaultBean}) automatically —
 * any {@code @ApplicationScoped} implementation in test scope takes precedence.
 *
 * <p>Test methods call {@link #register(String, DIDDocument)} to set up DID documents
 * and {@link #clear()} between tests to reset state.
 */
@ApplicationScoped
public class InjectableTestDIDResolver implements DIDResolver {

    private final Map<String, DIDDocument> docs = new ConcurrentHashMap<>();

    public InjectableTestDIDResolver register(final String did, final DIDDocument doc) {
        docs.put(did, doc);
        return this;
    }

    public void clear() {
        docs.clear();
    }

    @Override
    public Optional<DIDDocument> resolve(final String did) {
        return Optional.ofNullable(docs.get(did));
    }
}

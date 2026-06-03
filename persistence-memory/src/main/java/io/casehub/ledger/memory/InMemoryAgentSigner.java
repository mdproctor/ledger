package io.casehub.ledger.memory;

import java.security.KeyPair;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.casehub.ledger.runtime.service.AgentSignature;
import io.casehub.ledger.runtime.service.AgentSigner;

/**
 * In-memory {@link AgentSigner} for CDI integration tests.
 *
 * <p>Callers register key pairs per actorId via {@link #register}. Unregistered actors
 * return empty (unsigned). Thread-safe; suitable for concurrent test scenarios.
 *
 * <p>Call {@link #clear()} in {@code @BeforeEach} to reset state between tests.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class InMemoryAgentSigner implements AgentSigner {

    private final ConcurrentHashMap<String, KeyPair> keys = new ConcurrentHashMap<>();

    public void register(final String actorId, final KeyPair keyPair) {
        keys.put(actorId, keyPair);
    }

    public void clear() {
        keys.clear();
    }

    @Override
    public Optional<AgentSignature> sign(final String actorId, final byte[] data) {
        final KeyPair keyPair = keys.get(actorId);
        if (keyPair == null) {
            return Optional.empty();
        }
        return Optional.of(AgentSignature.signWith(keyPair, data));
    }
}

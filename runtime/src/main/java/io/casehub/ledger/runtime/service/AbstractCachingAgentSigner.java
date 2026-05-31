package io.casehub.ledger.runtime.service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base for {@link AgentSigner} implementations with per-actorId context caching.
 *
 * <p>Designed for external providers (Vault Transit, Cloud KMS, HSM via non-JCA API) where
 * {@link #loadContext} involves network or hardware I/O. The cache avoids redundant calls
 * on every {@code @PrePersist}.
 *
 * <p><strong>Cache semantics:</strong>
 * <ul>
 *   <li>{@link #loadContext} returns {@code Optional.empty()} → cached as absent; no further calls for this actor</li>
 *   <li>{@link #loadContext} throws → NOT cached; next {@link #sign} call retries</li>
 *   <li>Two threads hitting the same unconfigured actor simultaneously both call {@link #loadContext}
 *       (putIfAbsent, not computeIfAbsent). This is a deliberate trade-off: computeIfAbsent blocks
 *       the ConcurrentHashMap bucket for the duration of a network call and has reentrancy constraints;
 *       a duplicate load on cold start is cheaper than blocking unrelated actors.</li>
 * </ul>
 *
 * @param <C> per-actorId context type (e.g. {@code KeyPair} for extractable-key providers,
 *            {@code VaultTransitContext} for remote-signing providers)
 */
public abstract class AbstractCachingAgentSigner<C> implements AgentSigner {

    private final ConcurrentHashMap<String, Optional<C>> contextCache = new ConcurrentHashMap<>();

    @Override
    public final Optional<AgentSignature> sign(final String actorId, final byte[] data) {
        Optional<C> cached = contextCache.get(actorId);
        if (cached == null) {
            // loadContext throws on transient failure → not cached, caller retries next time
            final Optional<C> loaded = loadContext(actorId);
            final Optional<C> racing = contextCache.putIfAbsent(actorId, loaded);
            cached = racing != null ? racing : loaded;
        }
        return cached.map(ctx -> performSign(actorId, ctx, data));
    }

    /**
     * Loads signing context for {@code actorId}.
     *
     * @return {@code Optional.empty()} if not configured for signing (cached, no retry)
     * @throws RuntimeException for transient failures — NOT cached; next call retries
     */
    protected abstract Optional<C> loadContext(String actorId);

    /**
     * Performs the signing operation using the cached context.
     * Called only when context is present. Must not cache the result.
     */
    protected abstract AgentSignature performSign(String actorId, C context, byte[] data);

    /** Evicts all cached contexts. Next {@link #sign} call reloads from the source. */
    public void invalidateAll() {
        contextCache.clear();
    }

    /** Evicts the cached context for one actor. Next {@link #sign} call for this actor reloads. */
    public void invalidate(final String actorId) {
        contextCache.remove(actorId);
    }

    /**
     * Invalidates the cached context for the rotated actor.
     *
     * <p>Concrete {@code @ApplicationScoped} CDI subclasses should expose this as a CDI observer:
     * <pre>
     *   {@literal @}Observes
     *   public void onKeyRotated(AgentKeyRotatedEvent event) {
     *       super.onKeyRotated(event);
     *   }
     * </pre>
     * Direct {@code @Observes} on this abstract class is intentionally omitted: Quarkus Arc
     * registers abstract classes with observer methods as {@code @Dependent} beans, creating
     * an ambiguous {@link AgentSigner} resolution that breaks {@code @InjectMock} in tests.
     */
    public void onKeyRotated(final AgentKeyRotatedEvent event) {
        invalidate(event.actorId());
    }
}

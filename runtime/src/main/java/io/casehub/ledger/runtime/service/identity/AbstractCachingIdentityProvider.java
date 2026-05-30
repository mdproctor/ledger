package io.casehub.ledger.runtime.service.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base for identity-resolution implementations with TTL caching.
 *
 * <p>Eviction algorithm: on lookup, if a key exists but is expired, an atomic
 * conditional remove (remove(key, existing)) is performed before reloading.
 * This prevents stale values from being returned between the expiry check and
 * the remove. Two threads racing to evict the same entry are safe: only one
 * wins the conditional remove; the other falls through to a fresh put.
 *
 * <p>Transient failures (loadContext throws) are NOT cached — next call retries.
 * Empty results (not configured) ARE cached for the full TTL.
 *
 * <p>Subclasses that cannot implement loadContext (e.g. because they need entry
 * context beyond the cache key) should call {@link #put(String, Optional)} directly
 * from their own logic and let loadContext return empty as a no-op default.
 */
public abstract class AbstractCachingIdentityProvider<C> {

    private record CacheEntry<C>(Optional<C> value, Instant expiresAt) {
        boolean isExpired(Instant now) { return now.isAfter(expiresAt); }
    }

    private final ConcurrentHashMap<String, CacheEntry<C>> cache = new ConcurrentHashMap<>();
    private final Duration ttl;

    protected AbstractCachingIdentityProvider(Duration ttl) {
        this.ttl = ttl;
    }

    public final Optional<C> get(String key) {
        Instant now = now();
        CacheEntry<C> existing = cache.get(key);
        // Evict expired entries atomically before loading fresh value
        if (existing != null && existing.isExpired(now)) {
            cache.remove(key, existing); // atomic: only removes if value still == existing
            existing = null;
        }
        if (existing == null) {
            Optional<C> loaded = loadContext(key); // throws on transient → not cached
            CacheEntry<C> fresh = new CacheEntry<>(loaded, now.plus(ttl));
            CacheEntry<C> racing = cache.putIfAbsent(key, fresh);
            existing = racing != null ? racing : fresh;
        }
        return existing.value();
    }

    /** Directly stores a value, bypassing loadContext. Used by subclasses that compute values externally. */
    public final void put(String key, Optional<C> value) {
        cache.put(key, new CacheEntry<>(value, now().plus(ttl)));
    }

    /**
     * Loads context for the given key. Return empty to cache "not configured" result.
     * Throw to indicate a transient failure — exception propagates and result is not cached.
     * Subclasses that drive puts directly may return empty here as a no-op.
     */
    protected abstract Optional<C> loadContext(String key);

    /** Override in tests to control the clock. */
    protected Instant now() { return Instant.now(); }

    public void invalidate(String key) { cache.remove(key); }
    public void invalidateAll() { cache.clear(); }
}

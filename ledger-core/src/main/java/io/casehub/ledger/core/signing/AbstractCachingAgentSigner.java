package io.casehub.ledger.core.signing;

import io.casehub.ledger.core.model.AgentKeyRotatedEvent;

import java.security.PublicKey;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractCachingAgentSigner<C> implements AgentSigner {

    private final ConcurrentHashMap<String, Optional<C>> contextCache = new ConcurrentHashMap<>();

    @Override
    public final Optional<AgentSignature> sign(final String actorId, final byte[] data) {
        return resolveContext(actorId).map(ctx -> performSign(actorId, ctx, data));
    }

    @Override
    public Optional<AgentKeyMaterial> keyMaterial(final String actorId) {
        return resolveContext(actorId).map(ctx -> {
            final byte[] pub = contextPublicKey(ctx).getEncoded();
            return new AgentKeyMaterial(pub, AgentSignature.computeKeyRef(pub));
        });
    }

    protected Optional<C> resolveContext(final String actorId) {
        Optional<C> cached = contextCache.get(actorId);
        if (cached == null) {
            final Optional<C> loaded = loadContext(actorId);
            final Optional<C> racing = contextCache.putIfAbsent(actorId, loaded);
            cached = racing != null ? racing : loaded;
        }
        return cached;
    }

    protected abstract Optional<C> loadContext(String actorId);

    protected abstract AgentSignature performSign(String actorId, C context, byte[] data);

    protected abstract PublicKey contextPublicKey(C context);

    public void invalidateAll() {
        contextCache.clear();
    }

    public void invalidate(final String actorId) {
        contextCache.remove(actorId);
    }

    public void onKeyRotated(final AgentKeyRotatedEvent event) {
        invalidate(event.actorId());
    }
}

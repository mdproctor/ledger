package io.casehub.ledger.runtime.privacy;

import java.util.Optional;

import io.casehub.ledger.api.spi.ActorIdentityProvider;
import io.casehub.platform.api.identity.ActorType;

/** Pass-through implementation — stores raw actor identities unchanged. */
public class PassThroughActorIdentityProvider implements ActorIdentityProvider {

    @Override
    public String tokenise(final String rawActorId, final ActorType actorType) {
        return rawActorId;
    }

    @Override
    public Optional<String> tokeniseForQuery(final String rawActorId) {
        return Optional.ofNullable(rawActorId);
    }

    @Override
    public Optional<String> resolve(final String token) {
        return Optional.ofNullable(token);
    }

    @Override
    public void erase(final String rawActorId) {
        // pass-through: no mapping to sever
    }
}

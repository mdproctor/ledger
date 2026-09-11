package io.casehub.ledger.core.privacy;

import java.util.Optional;

import io.casehub.ledger.api.spi.ActorIdentityProvider;
import io.casehub.platform.api.identity.ActorType;

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
    }
}

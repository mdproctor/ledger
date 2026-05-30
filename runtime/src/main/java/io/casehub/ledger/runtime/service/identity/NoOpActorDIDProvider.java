package io.casehub.ledger.runtime.service.identity;

import io.casehub.ledger.api.spi.identity.ActorDIDProvider;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

/** Default no-op implementation. Zero behavior change for consumers that don't configure DIDs. */
@ApplicationScoped
@DefaultBean
public class NoOpActorDIDProvider implements ActorDIDProvider {
    @Override
    public Optional<String> didFor(final String actorId) {
        return Optional.empty();
    }
}

package io.casehub.ledger.runtime.service.identity;

import io.casehub.platform.api.identity.ActorDIDProvider;
import io.casehub.ledger.core.model.AgentKeyRotatedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Bridges ledger key-rotation events to platform identity cache invalidation.
 *
 * <p>Delegates to {@link ActorDIDProvider#invalidate(String)}, which is a default
 * method on the SPI. {@code CompositeActorDIDProvider} propagates to all registered
 * providers; {@code ScimActorDIDProvider} evicts its underlying SCIM lookup cache.
 */
@ApplicationScoped
public class IdentityCacheInvalidator {

    @Inject
    ActorDIDProvider actorDIDProvider;

    void onKeyRotated(@Observes final AgentKeyRotatedEvent event) {
        actorDIDProvider.invalidate(event.actorId());
    }
}

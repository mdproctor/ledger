package io.casehub.ledger.runtime.service.identity;

import io.casehub.ledger.api.spi.identity.ActorDIDProvider;
import io.casehub.ledger.runtime.config.LedgerConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;

/**
 * Reads DID URIs from configuration.
 * Config key: {@code casehub.ledger.agent-identity.dids."claude:reviewer@v1"=did:web:...}
 * Quote the key in application.properties to handle the colon in actorId strings.
 */
@ApplicationScoped
public class ConfiguredActorDIDProvider implements ActorDIDProvider {

    @Inject
    LedgerConfig config;

    @Override
    public Optional<String> didFor(final String actorId) {
        if (actorId == null) return Optional.empty();
        return Optional.ofNullable(config.agentIdentity().dids().get(actorId));
    }
}

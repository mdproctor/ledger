package io.casehub.ledger.signing.vault.quarkus;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import io.casehub.ledger.core.model.AgentKeyRotatedEvent;
import io.casehub.ledger.signing.vault.VaultTransitAgentSignerCore;
import io.casehub.ledger.signing.vault.VaultTransitAuthConfig;
import io.casehub.ledger.signing.vault.VaultTransitSigningClient;
import io.casehub.ledger.signing.vault.VaultTransitSigningConfig;
import io.casehub.ledger.signing.vault.VaultTokenSource;
import io.quarkus.scheduler.Scheduled;

@ApplicationScoped
@Alternative
@Priority(1)
public class VaultTransitAgentSigner extends VaultTransitAgentSignerCore {

    @Inject
    public VaultTransitAgentSigner(final VaultTransitConfig config) {
        super(new VaultTransitSigningConfig(config.address(), config.keyMapping()),
                mapAuthConfig(config.auth()));
    }

    VaultTransitAgentSigner(final VaultTransitConfig config, final VaultTransitSigningClient client,
            final VaultTokenSource tokenSource) {
        super(new VaultTransitSigningConfig(config.address(), config.keyMapping()), client, tokenSource);
    }

    private static VaultTransitAuthConfig mapAuthConfig(final VaultTransitConfig.AuthConfig auth) {
        return new VaultTransitAuthConfig(
                mapAuthMethod(auth.method()),
                auth.token(),
                auth.roleId(),
                auth.secretId(),
                auth.role(),
                auth.jwtPath(),
                auth.jwt(),
                auth.mountPath());
    }

    private static VaultTransitAuthConfig.AuthMethod mapAuthMethod(
            final VaultTransitConfig.AuthMethod method) {
        return switch (method) {
            case TOKEN -> VaultTransitAuthConfig.AuthMethod.TOKEN;
            case APPROLE -> VaultTransitAuthConfig.AuthMethod.APPROLE;
            case KUBERNETES -> VaultTransitAuthConfig.AuthMethod.KUBERNETES;
            case JWT -> VaultTransitAuthConfig.AuthMethod.JWT;
        };
    }

    @Scheduled(every = "${casehub.ledger.vault-transit.refresh-interval:5m}")
    void refreshCache() {
        invalidateAll();
    }

    public void onKeyRotated(@Observes final AgentKeyRotatedEvent event) {
        super.onKeyRotated(event);
    }
}
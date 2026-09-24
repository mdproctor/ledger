package io.casehub.ledger.signing.azure.quarkus;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import io.casehub.ledger.core.model.AgentKeyRotatedEvent;
import io.casehub.ledger.signing.azure.AzureKeyVaultAgentSignerCore;
import io.casehub.ledger.signing.azure.AzureKeyVaultClientWrapper;
import io.casehub.ledger.signing.azure.AzureKeyVaultSigningClient;
import io.quarkus.scheduler.Scheduled;

@ApplicationScoped
@Alternative
@Priority(1)
public class AzureKeyVaultAgentSigner extends AzureKeyVaultAgentSignerCore {

    @Inject
    public AzureKeyVaultAgentSigner(final AzureKeyVaultConfig config) {
        super(config.keyMapping());
    }

    AzureKeyVaultAgentSigner(final AzureKeyVaultConfig config,
            final AzureKeyVaultClientWrapper wrapper) {
        super(config.keyMapping(), wrapper);
    }

    @Scheduled(every = "${casehub.ledger.azure-keyvault.refresh-interval:24h}")
    void refreshCache() {
        invalidateAll();
    }

    public void onKeyRotated(@Observes final AgentKeyRotatedEvent event) {
        super.onKeyRotated(event);
    }
}
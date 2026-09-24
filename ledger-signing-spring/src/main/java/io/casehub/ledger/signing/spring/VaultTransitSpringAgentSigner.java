package io.casehub.ledger.signing.spring;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import io.casehub.ledger.core.model.AgentKeyRotatedEvent;
import io.casehub.ledger.signing.vault.VaultTransitAgentSignerCore;
import io.casehub.ledger.signing.vault.VaultTransitAuthConfig;
import io.casehub.ledger.signing.vault.VaultTransitSigningConfig;

public class VaultTransitSpringAgentSigner extends VaultTransitAgentSignerCore {

    public VaultTransitSpringAgentSigner(final VaultTransitSigningConfig config,
            final VaultTransitAuthConfig authConfig) {
        super(config, authConfig);
    }

    @Scheduled(fixedRateString = "${casehub.ledger.vault-transit.refresh-interval-ms:300000}")
    void refreshCache() {
        invalidateAll();
    }

    @EventListener
    public void onKeyRotated(final AgentKeyRotatedEvent event) {
        super.onKeyRotated(event);
    }
}

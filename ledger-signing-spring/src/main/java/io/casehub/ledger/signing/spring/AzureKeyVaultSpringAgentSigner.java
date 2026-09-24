package io.casehub.ledger.signing.spring;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import io.casehub.ledger.core.model.AgentKeyRotatedEvent;
import io.casehub.ledger.signing.azure.AzureKeyVaultAgentSignerCore;

public class AzureKeyVaultSpringAgentSigner extends AzureKeyVaultAgentSignerCore {

    public AzureKeyVaultSpringAgentSigner(final Map<String, String> keyMapping) {
        super(keyMapping);
    }

    @Scheduled(fixedRateString = "${casehub.ledger.azure-keyvault.refresh-interval-ms:86400000}")
    void refreshCache() {
        invalidateAll();
    }

    @EventListener
    public void onKeyRotated(final AgentKeyRotatedEvent event) {
        super.onKeyRotated(event);
    }
}

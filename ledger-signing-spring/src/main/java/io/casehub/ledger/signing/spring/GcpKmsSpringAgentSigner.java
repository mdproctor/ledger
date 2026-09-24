package io.casehub.ledger.signing.spring;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import io.casehub.ledger.core.model.AgentKeyRotatedEvent;
import io.casehub.ledger.signing.gcp.GcpKmsAgentSignerCore;

public class GcpKmsSpringAgentSigner extends GcpKmsAgentSignerCore {

    public GcpKmsSpringAgentSigner(final Map<String, String> keyMapping) {
        super(keyMapping);
    }

    @Scheduled(fixedRateString = "${casehub.ledger.gcp-kms.refresh-interval-ms:300000}")
    void refreshCache() {
        invalidateAll();
    }

    @EventListener
    public void onKeyRotated(final AgentKeyRotatedEvent event) {
        super.onKeyRotated(event);
    }
}

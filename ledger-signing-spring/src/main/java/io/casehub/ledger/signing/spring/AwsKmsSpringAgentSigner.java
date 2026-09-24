package io.casehub.ledger.signing.spring;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import io.casehub.ledger.core.model.AgentKeyRotatedEvent;
import io.casehub.ledger.signing.aws.AwsKmsAgentSignerCore;
import io.casehub.ledger.signing.aws.AwsKmsSigningConfig;

public class AwsKmsSpringAgentSigner extends AwsKmsAgentSignerCore {

    public AwsKmsSpringAgentSigner(final AwsKmsSigningConfig config) {
        super(config);
    }

    @Scheduled(fixedRateString = "${casehub.ledger.aws-kms.refresh-interval-ms:300000}")
    void refreshCache() {
        invalidateAll();
    }

    @EventListener
    public void onKeyRotated(final AgentKeyRotatedEvent event) {
        super.onKeyRotated(event);
    }
}

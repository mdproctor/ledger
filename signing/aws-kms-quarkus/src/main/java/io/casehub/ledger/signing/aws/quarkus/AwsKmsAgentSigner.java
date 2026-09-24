package io.casehub.ledger.signing.aws.quarkus;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import io.casehub.ledger.core.model.AgentKeyRotatedEvent;
import io.casehub.ledger.signing.aws.AwsKmsAgentSignerCore;
import io.casehub.ledger.signing.aws.AwsKmsSigningConfig;
import io.quarkus.scheduler.Scheduled;
import software.amazon.awssdk.services.kms.KmsClient;

@ApplicationScoped
@Alternative
@Priority(1)
public class AwsKmsAgentSigner extends AwsKmsAgentSignerCore {

    @Inject
    public AwsKmsAgentSigner(final AwsKmsConfig config) {
        super(new AwsKmsSigningConfig(config.region(), config.keyMapping()));
    }

    AwsKmsAgentSigner(final AwsKmsConfig config, final KmsClient kmsClient) {
        super(new AwsKmsSigningConfig(config.region(), config.keyMapping()), kmsClient);
    }

    @Scheduled(every = "${casehub.ledger.aws-kms.refresh-interval:5m}")
    void refreshCache() {
        invalidateAll();
    }

    public void onKeyRotated(@Observes final AgentKeyRotatedEvent event) {
        super.onKeyRotated(event);
    }
}
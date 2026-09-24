package io.casehub.ledger.signing.gcp.quarkus;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import io.casehub.ledger.core.model.AgentKeyRotatedEvent;
import io.casehub.ledger.signing.gcp.GcpKmsAgentSignerCore;
import io.casehub.ledger.signing.gcp.GcpKmsSigningClient;
import io.quarkus.scheduler.Scheduled;

@ApplicationScoped
@Alternative
@Priority(1)
public class GcpKmsAgentSigner extends GcpKmsAgentSignerCore {

    @Inject
    public GcpKmsAgentSigner(final GcpKmsConfig config) {
        super(config.keyMapping());
    }

    GcpKmsAgentSigner(final GcpKmsConfig config, final GcpKmsSigningClient client) {
        super(config.keyMapping(), client);
    }

    @Scheduled(every = "${casehub.ledger.gcp-kms.refresh-interval:5m}")
    void refreshCache() {
        invalidateAll();
    }

    public void onKeyRotated(@Observes final AgentKeyRotatedEvent event) {
        super.onKeyRotated(event);
    }
}
package io.casehub.ledger.runtime.service.identity;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.core.enricher.LedgerEntryEnricher;
import io.casehub.ledger.jpa.ActorIdentityBindingEntry;
import io.casehub.platform.api.identity.ActorDIDProvider;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Priority(40)
public class ActorDIDEnricher implements LedgerEntryEnricher {

    private final io.casehub.ledger.core.service.identity.ActorDIDEnricherCore core;

    @Inject
    public ActorDIDEnricher(ActorDIDProvider provider) {
        this.core = new io.casehub.ledger.core.service.identity.ActorDIDEnricherCore(
                provider, e -> e instanceof ActorIdentityBindingEntry);
    }

    @Override
    public void enrich(LedgerEntry entry) {
        core.enrich(entry);
    }

    @Override
    public int priority() {
        return core.priority();
    }
}

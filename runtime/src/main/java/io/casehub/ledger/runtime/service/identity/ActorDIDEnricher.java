package io.casehub.ledger.runtime.service.identity;

import io.casehub.ledger.api.spi.identity.ActorDIDProvider;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.service.LedgerEntryEnricher;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Populates {@link LedgerEntry#actorDid} from the configured {@link ActorDIDProvider}.
 * No-op when the actor has no DID configured. Non-fatal — exceptions are logged and swallowed.
 */
@ApplicationScoped
@Priority(40)
public class ActorDIDEnricher implements LedgerEntryEnricher {

    private static final Logger LOG = Logger.getLogger(ActorDIDEnricher.class);
    private final ActorDIDProvider provider;

    @Inject
    public ActorDIDEnricher(final ActorDIDProvider provider) {
        this.provider = provider;
    }

    @Override
    public void enrich(final LedgerEntry entry) {
        if (entry.actorId == null || entry.actorDid != null) return;
        try {
            provider.didFor(entry.actorId).ifPresent(did -> entry.actorDid = did);
        } catch (final Exception e) {
            LOG.warnf("ActorDIDEnricher failed for actor %s: %s", entry.actorId, e.getMessage());
        }
    }
}

package io.casehub.ledger.core.service.identity;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.core.enricher.LedgerEntryEnricher;
import io.casehub.platform.api.identity.ActorDIDProvider;

import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ActorDIDEnricherCore implements LedgerEntryEnricher {

    private static final Logger log = Logger.getLogger(ActorDIDEnricherCore.class.getName());

    private final ActorDIDProvider provider;
    private final Predicate<LedgerEntry> skipPredicate;

    public ActorDIDEnricherCore(ActorDIDProvider provider) {
        this(provider, e -> false);
    }

    public ActorDIDEnricherCore(ActorDIDProvider provider, Predicate<LedgerEntry> skipPredicate) {
        this.provider = provider;
        this.skipPredicate = skipPredicate;
    }

    @Override
    public void enrich(LedgerEntry entry) {
        if (entry.actorId == null || entry.actorDid != null) return;
        if (skipPredicate.test(entry)) return;
        try {
            provider.didFor(entry.actorId).ifPresent(did -> entry.actorDid = did);
        } catch (Exception e) {
            log.log(Level.WARNING, "ActorDIDEnricherCore failed for actor " + entry.actorId + ": " + e.getMessage());
        }
    }

    @Override
    public int priority() {
        return 40;
    }
}

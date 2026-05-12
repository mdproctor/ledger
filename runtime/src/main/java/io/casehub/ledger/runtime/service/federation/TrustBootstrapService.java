package io.casehub.ledger.runtime.service.federation;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Seeds trust scores for actors appearing in this deployment for the first time.
 *
 * <p>
 * Called from {@link io.casehub.ledger.runtime.service.TrustScoreJob} as a batch pre-pass
 * before the per-actor computation loop. Only fires when
 * {@code casehub.ledger.trust-score.bootstrap.enabled=true}.
 */
@ApplicationScoped
public class TrustBootstrapService {

    @Inject
    TrustBootstrapSource bootstrapSource;

    @Inject
    TrustImportService importService;

    /**
     * For each actor ID in {@code newActorIds}, fetch prior trust from the configured source
     * and import it via {@link TrustImportService}. Actors for which the source returns empty
     * are silently skipped — they start from Beta(1,1).
     *
     * @param newActorIds actor IDs with no existing {@link io.casehub.ledger.runtime.model.ActorTrustScore} row
     */
    public void bootstrapIfNew(final Set<String> newActorIds) {
        for (final String actorId : newActorIds) {
            bootstrapSource.fetchPriorTrust(actorId)
                    .ifPresent(importService::importTrust);
        }
    }
}

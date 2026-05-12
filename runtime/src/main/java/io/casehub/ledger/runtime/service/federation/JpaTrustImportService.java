package io.casehub.ledger.runtime.service.federation;

import java.time.Instant;

import io.casehub.ledger.api.model.ActorTrustScore.ScoreType;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Seed-if-absent import strategy.
 *
 * <p>
 * For each actor in the payload: if a GLOBAL row already exists for that actor, skip
 * the entire actor. Otherwise, write GLOBAL, CAPABILITY, and DIMENSION rows from the export.
 * This ensures bootstrap seeds are written once and never overwrite locally-computed scores.
 *
 * <p>
 * Activate via:
 * {@code quarkus.arc.selected-alternatives=io.casehub.ledger.runtime.service.federation.JpaTrustImportService}
 * (alongside any other selected alternatives already configured).
 *
 * <p>
 * For custom merge behaviour, implement {@link TrustImportService} directly.
 */
@Alternative
@ApplicationScoped
public class JpaTrustImportService implements TrustImportService {

    @Inject
    ActorTrustScoreRepository trustRepo;

    @Override
    @Transactional
    public void importTrust(final TrustExportPayload payload) {
        final Instant now = Instant.now();
        for (final ActorExport actor : payload.actors()) {
            if (trustRepo.findByActorId(actor.actorId()).isPresent()) {
                continue;
            }
            seedActor(actor, now);
        }
    }

    private void seedActor(final ActorExport actor, final Instant now) {
        if (actor.globalScore() != null) {
            final GlobalScoreExport g = actor.globalScore();
            trustRepo.upsert(actor.actorId(), ScoreType.GLOBAL, null,
                    actor.actorType(), g.trustScore(),
                    g.decisionCount(), 0, g.alpha(), g.beta(),
                    g.attestationPositive(), g.attestationNegative(), now);
        }
        for (final CapabilityScoreExport c : actor.capabilityScores()) {
            trustRepo.upsert(actor.actorId(), ScoreType.CAPABILITY, c.capabilityTag(),
                    actor.actorType(), c.trustScore(),
                    c.decisionCount(), 0, c.alpha(), c.beta(),
                    c.attestationPositive(), c.attestationNegative(), now);
        }
        for (final DimensionScoreExport d : actor.dimensionScores()) {
            trustRepo.upsert(actor.actorId(), ScoreType.DIMENSION, d.dimension(),
                    actor.actorType(), d.score(),
                    d.sampleCount(), 0, 0.0, 0.0,
                    d.sampleCount(), 0, now);
        }
    }
}

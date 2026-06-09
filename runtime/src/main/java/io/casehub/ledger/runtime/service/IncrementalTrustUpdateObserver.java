package io.casehub.ledger.runtime.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import org.jboss.logging.Logger;

import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.qualifier.CrossTenant;
import io.casehub.ledger.runtime.repository.CrossTenantLedgerEntryRepository;
import io.casehub.ledger.runtime.service.routing.TrustScoreActorUpdatedEvent;

/**
 * CDI observer that triggers immediate per-actor trust score recomputation
 * when an attestation is persisted.
 *
 * <p>
 * Listens for {@link AttestationRecordedEvent} fired by
 * {@link LedgerEntryRepository#saveAttestation}. Fires only after the
 * attestation's transaction commits ({@code AFTER_SUCCESS}), then opens
 * a fresh transaction ({@code REQUIRES_NEW}) to read decisions and
 * attestations for the actor and recompute all trust score types via
 * {@link PerActorTrustComputer}.
 *
 * <p>
 * Gated by {@code casehub.ledger.trust-score.enabled},
 * {@code casehub.ledger.trust-score.incremental.enabled}, and
 * {@code casehub.ledger.trust-score.materialization.enabled} — when any
 * is {@code false}, the observer returns immediately.
 *
 * <p>
 * The nightly {@link TrustScoreJob} remains as a consistency backstop.
 */
@ApplicationScoped
public class IncrementalTrustUpdateObserver {

    private static final Logger log = Logger.getLogger(IncrementalTrustUpdateObserver.class);

    @Inject
    LedgerConfig config;

    @Inject
    @CrossTenant
    CrossTenantLedgerEntryRepository ledgerRepo;

    @Inject
    PerActorTrustComputer perActorComputer;

    @Inject
    Event<TrustScoreActorUpdatedEvent> actorUpdatedEvent;

    @Transactional(TxType.REQUIRES_NEW)
    void onAttestationRecorded(
            @Observes(during = TransactionPhase.AFTER_SUCCESS)
            final AttestationRecordedEvent event) {

        if (!config.trustScore().enabled()
                || !config.trustScore().incremental().enabled()
                || !config.trustScore().materialization().enabled()) {
            return;
        }

        try {
            final Instant now = Instant.now();
            final List<LedgerEntry> decisions = ledgerRepo.findEventsByActorId(event.actorId());

            if (decisions.isEmpty()) {
                return;
            }

            final Map<UUID, List<LedgerAttestation>> attestationsByEntry =
                    ledgerRepo.findAttestationsByActorId(event.actorId());

            final List<ActorTrustScore> scores =
                    perActorComputer.computeForActor(event.actorId(), decisions, attestationsByEntry, now);

            actorUpdatedEvent.fire(new TrustScoreActorUpdatedEvent(event.actorId(), scores, now));

            log.debugf("Incremental trust recomputation completed for actor=%s, scores=%d",
                    event.actorId(), scores.size());
        } catch (final Exception e) {
            log.errorf(e, "Incremental trust recomputation failed for actor=%s, entryId=%s — "
                    + "batch job will catch up on next run", event.actorId(), event.ledgerEntryId());
        }
    }
}

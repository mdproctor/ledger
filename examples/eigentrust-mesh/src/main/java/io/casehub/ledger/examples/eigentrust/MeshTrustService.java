package io.casehub.ledger.examples.eigentrust;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.transaction.Transactional;

import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import io.casehub.ledger.runtime.service.TrustScoreJob;

/**
 * Orchestrates the document classification mesh scenario.
 *
 * <p>
 * Three AI agents classify documents. After each classification, peer agents
 * attest to each other's work. The resulting attestation graph demonstrates
 * how EigenTrust propagates trust transitively.
 *
 * <p>
 * Agent roles:
 * <ul>
 *   <li>{@code classifier-a} — reliable agent; attested SOUND by b and c</li>
 *   <li>{@code classifier-b} — somewhat reliable; attested SOUND by c, CHALLENGED by a</li>
 *   <li>{@code classifier-c} — unreliable; attested FLAGGED by a and b</li>
 * </ul>
 */
@ApplicationScoped
public class MeshTrustService {

    static final String AGENT_A = "claude:classifier-a@v1";
    static final String AGENT_B = "claude:classifier-b@v1";
    static final String AGENT_C = "claude:classifier-c@v1";

    @Inject
    LedgerEntryRepository repo;

    @Inject
    TrustScoreJob trustScoreJob;

    @Inject
    ActorTrustScoreRepository trustRepo;

    /**
     * @deprecated Use {@link #seedEntries()} then {@link #seedAttestations(SeedResult)} for correct TX boundaries.
     */
    @Deprecated
    public void seedClassifications() {
        throw new UnsupportedOperationException("Use seedEntries() then seedAttestations() separately");
    }

    public record SeedResult(
            DocumentClassificationLedgerEntry a1, DocumentClassificationLedgerEntry a2,
            DocumentClassificationLedgerEntry b1, DocumentClassificationLedgerEntry b2,
            DocumentClassificationLedgerEntry c1, DocumentClassificationLedgerEntry c2) {}

    @Transactional
    public SeedResult seedEntries() {
        final Instant base = Instant.now().minus(1, ChronoUnit.DAYS);
        return new SeedResult(
                classify(AGENT_A, "HIGH", base),
                classify(AGENT_A, "HIGH", base.plusSeconds(60)),
                classify(AGENT_B, "MEDIUM", base.plusSeconds(120)),
                classify(AGENT_B, "MEDIUM", base.plusSeconds(180)),
                classify(AGENT_C, "LOW", base.plusSeconds(240)),
                classify(AGENT_C, "LOW", base.plusSeconds(300)));
    }

    @Transactional
    public void seedAttestations(final SeedResult e) {
        final Instant attBase = Instant.now().minus(1, ChronoUnit.DAYS).plusSeconds(600);

        attest(AGENT_B, e.a1, AttestationVerdict.SOUND, 0.9, attBase);
        attest(AGENT_C, e.a1, AttestationVerdict.SOUND, 0.8, attBase.plusSeconds(10));
        attest(AGENT_B, e.a2, AttestationVerdict.SOUND, 0.9, attBase.plusSeconds(20));
        attest(AGENT_C, e.a2, AttestationVerdict.ENDORSED, 0.85, attBase.plusSeconds(30));

        attest(AGENT_C, e.b1, AttestationVerdict.SOUND, 0.7, attBase.plusSeconds(40));
        attest(AGENT_A, e.b1, AttestationVerdict.CHALLENGED, 0.8, attBase.plusSeconds(50));
        attest(AGENT_C, e.b2, AttestationVerdict.SOUND, 0.7, attBase.plusSeconds(60));
        attest(AGENT_A, e.b2, AttestationVerdict.CHALLENGED, 0.8, attBase.plusSeconds(70));

        attest(AGENT_A, e.c1, AttestationVerdict.FLAGGED, 0.95, attBase.plusSeconds(80));
        attest(AGENT_B, e.c1, AttestationVerdict.FLAGGED, 0.9, attBase.plusSeconds(90));
        attest(AGENT_A, e.c2, AttestationVerdict.FLAGGED, 0.95, attBase.plusSeconds(100));
        attest(AGENT_B, e.c2, AttestationVerdict.FLAGGED, 0.9, attBase.plusSeconds(110));
    }

    /**
     * Triggers trust score computation (both Bayesian Beta and EigenTrust).
     * Scheduler is disabled in tests — call this directly.
     */
    public void runTrustComputation() {
        trustScoreJob.runComputation();
    }

    /**
     * Returns all computed {@link ActorTrustScore} records.
     */
    public List<ActorTrustScore> getScores() {
        return trustRepo.findAll();
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private DocumentClassificationLedgerEntry classify(
            final String agentId,
            final String riskLevel,
            final Instant occurredAt) {

        final DocumentClassificationLedgerEntry entry = new DocumentClassificationLedgerEntry();
        entry.subjectId      = UUID.randomUUID();
        entry.sequenceNumber = 1;
        entry.entryType      = LedgerEntryType.EVENT;
        entry.actorId        = agentId;
        entry.actorType      = ActorType.AGENT;
        entry.actorRole      = "DocumentClassifier";
        entry.occurredAt     = occurredAt;
        entry.documentId     = UUID.randomUUID();
        entry.riskLevel      = riskLevel;
        return (DocumentClassificationLedgerEntry) repo.save(entry, TenancyConstants.DEFAULT_TENANT_ID);
    }

    private void attest(
            final String attestorId,
            final DocumentClassificationLedgerEntry entry,
            final AttestationVerdict verdict,
            final double confidence,
            final Instant occurredAt) {

        final LedgerAttestation att = new LedgerAttestation();
        att.ledgerEntryId = entry.id;
        att.subjectId     = entry.subjectId;
        att.attestorId    = attestorId;
        att.attestorType  = ActorType.AGENT;
        att.attestorRole  = "PeerReviewer";
        att.verdict       = verdict;
        att.confidence    = confidence;
        att.occurredAt    = occurredAt;
        repo.saveAttestation(att, TenancyConstants.DEFAULT_TENANT_ID);
    }
}

package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.CapabilityTag;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.service.supplement.TestEntry;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Integration tests for {@link io.casehub.ledger.runtime.service.IncrementalTrustUpdateObserver}.
 *
 * <p>
 * Verifies that persisting an attestation via {@link LedgerEntryRepository#saveAttestation}
 * triggers immediate per-actor trust score recomputation through the CDI observer pipeline:
 * {@code saveAttestation → AttestationRecordedEvent → IncrementalTrustUpdateObserver →
 * PerActorTrustComputer → upsert trust scores}.
 *
 * <p>
 * Tests do NOT use {@code @Transactional} on the test method. The observer fires at
 * {@code TransactionPhase.AFTER_SUCCESS} — the transaction must actually commit for the
 * observer to trigger. Each data mutation uses {@link QuarkusTransaction#requiringNew()}
 * to commit eagerly, and reads use separate transactions to see committed data.
 */
@QuarkusTest
@TestProfile(IncrementalTrustUpdateIT.Profile.class)
class IncrementalTrustUpdateIT {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "incremental-trust-test";
        }
    }

    @Inject
    LedgerEntryRepository repo;

    @Inject
    ActorTrustScoreRepository trustRepo;

    @Test
    void attestationPersist_triggersIncrementalRecomputation() {
        final String actorId = "incr-agent-" + UUID.randomUUID();
        final Instant now = Instant.now();

        // Seed two decisions without attestations — no trust score yet
        final UUID entry1Id = seedEvent(actorId, now.minus(2, ChronoUnit.DAYS));
        seedEvent(actorId, now.minus(1, ChronoUnit.DAYS));

        assertThat(readGlobalScore(actorId)).isNull();

        // Persist an attestation — this triggers IncrementalTrustUpdateObserver
        QuarkusTransaction.requiringNew().run(() -> {
            final LedgerAttestation att = new LedgerAttestation();
            att.ledgerEntryId = entry1Id;
            att.subjectId = UUID.randomUUID(); // attestation subjectId
            att.attestorId = "compliance-bot";
            att.attestorType = ActorType.AGENT;
            att.verdict = AttestationVerdict.SOUND;
            att.confidence = 1.0;
            att.capabilityTag = CapabilityTag.GLOBAL;
            att.occurredAt = now;
            repo.saveAttestation(att);
        });

        // Score should now exist — incremental recomputation ran
        final ActorTrustScore score = readGlobalScore(actorId);
        assertThat(score).isNotNull();
        assertThat(score.trustScore).isGreaterThan(0.5);
        assertThat(score.decisionCount).isEqualTo(2);
    }

    @Test
    void capabilityAttestation_producesCapabilityScore() {
        final String actorId = "incr-cap-agent-" + UUID.randomUUID();
        final Instant now = Instant.now();

        final UUID entryId = seedEvent(actorId, now.minus(1, ChronoUnit.DAYS));

        QuarkusTransaction.requiringNew().run(() -> {
            final LedgerAttestation att = new LedgerAttestation();
            att.ledgerEntryId = entryId;
            att.subjectId = UUID.randomUUID();
            att.attestorId = "compliance-bot";
            att.attestorType = ActorType.AGENT;
            att.verdict = AttestationVerdict.SOUND;
            att.confidence = 1.0;
            att.capabilityTag = "code-review";
            att.occurredAt = now;
            repo.saveAttestation(att);
        });

        assertThat(readCapabilityScore(actorId, "code-review")).isPresent();
    }

    /**
     * Seeds an EVENT entry in a committed transaction and returns its ID.
     */
    private UUID seedEvent(final String actorId, final Instant occurredAt) {
        return QuarkusTransaction.requiringNew().call(() -> {
            final TestEntry entry = new TestEntry();
            entry.subjectId = UUID.randomUUID();
            entry.entryType = LedgerEntryType.EVENT;
            entry.actorId = actorId;
            entry.actorType = ActorType.AGENT;
            entry.actorRole = "Classifier";
            entry.occurredAt = occurredAt.truncatedTo(ChronoUnit.MILLIS);
            return repo.save(entry).id;
        });
    }

    /**
     * Reads the GLOBAL trust score in its own transaction.
     */
    private ActorTrustScore readGlobalScore(final String actorId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> trustRepo.findByActorId(actorId).orElse(null));
    }

    /**
     * Reads a CAPABILITY trust score in its own transaction.
     */
    private java.util.Optional<ActorTrustScore> readCapabilityScore(
            final String actorId, final String capabilityTag) {
        return QuarkusTransaction.requiringNew()
                .call(() -> trustRepo.findCapabilityScore(actorId, capabilityTag));
    }
}

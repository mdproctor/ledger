package io.casehub.ledger.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.model.supplement.ComplianceSupplement;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.service.supplement.TestEntry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;

/**
 * Integration tests verifying write-path and query-path privacy wiring
 * in {@link io.casehub.ledger.runtime.repository.jpa.JpaLedgerEntryRepository}.
 */
@QuarkusTest
@TestProfile(InternalActorIdentityProviderIT.PseudonymisationProfile.class)
class LedgerPrivacyWiringIT {

    @Inject
    LedgerEntryRepository repo;

    @Inject
    EntityManager em;

    @Inject
    io.casehub.ledger.api.spi.OutcomeRecorder outcomeRecorder;

    // ── Happy path: actorId stored as token, not raw ──────────────────────────

    @Test
    @Transactional
    void save_actorIdStoredAsToken_notRawIdentity() {
        final String rawActorId = "alice-" + UUID.randomUUID();
        final TestEntry entry = entry(rawActorId);
        repo.save(entry, DEFAULT_TENANT_ID);

        final LedgerEntry stored = em.find(LedgerEntry.class, entry.id);
        assertThat(stored.actorId)
                .isNotNull()
                .isNotEqualTo(rawActorId);
    }

    // ── Happy path: findByActorId translates raw → token transparently ─────────

    @Test
    @Transactional
    void findByActorId_withRawActorId_returnsEntry() {
        final String rawActorId = "bob-" + UUID.randomUUID();
        final Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        final Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

        repo.save(entry(rawActorId), DEFAULT_TENANT_ID);

        final List<LedgerEntry> results = repo.findByActorId(rawActorId, from, to, DEFAULT_TENANT_ID);
        assertThat(results).hasSize(1);
    }

    // ── Correctness: attestorId tokenised on saveAttestation ─────────────────

    @Test
    @Transactional
    void saveAttestation_attestorIdStoredAsToken() {
        final String rawActorId = "carol-" + UUID.randomUUID();
        final String rawAttestorId = "attestor-" + UUID.randomUUID();
        final TestEntry entry = entry(rawActorId);
        repo.save(entry, DEFAULT_TENANT_ID);

        final LedgerAttestation att = new LedgerAttestation();
        att.id = UUID.randomUUID();
        att.ledgerEntryId = entry.id;
        att.subjectId = entry.subjectId;
        att.attestorId = rawAttestorId;
        att.attestorType = ActorType.HUMAN;
        att.verdict = AttestationVerdict.SOUND;
        att.confidence = 0.9;
        att.occurredAt = Instant.now();
        repo.saveAttestation(att, DEFAULT_TENANT_ID);

        final LedgerAttestation stored = em.find(LedgerAttestation.class, att.id);
        assertThat(stored.attestorId)
                .isNotNull()
                .isNotEqualTo(rawAttestorId);
    }

    // ── Happy path: decisionContext passed through pass-through sanitiser ─────

    @Test
    @Transactional
    void save_decisionContext_storedUnchanged_withPassThroughSanitiser() {
        final String rawActorId = "dave-" + UUID.randomUUID();
        final TestEntry entry = entry(rawActorId);
        final ComplianceSupplement cs = new ComplianceSupplement();
        cs.decisionContext = "{\"riskScore\":42,\"region\":\"EU\"}";
        entry.attach(cs);

        repo.save(entry, DEFAULT_TENANT_ID);

        final LedgerEntry stored = em.find(LedgerEntry.class, entry.id);
        stored.supplements.size(); // force lazy load
        assertThat(stored.compliance())
                .isPresent()
                .hasValueSatisfying(c -> assertThat(c.decisionContext).isEqualTo("{\"riskScore\":42,\"region\":\"EU\"}"));
    }

    // ── Correctness: same actorId on two entries → same token ─────────────────

    @Test
    @Transactional
    void save_sameActorId_twoEntries_sameTokenStored() {
        final String rawActorId = "eve-" + UUID.randomUUID();
        final TestEntry e1 = entry(rawActorId);
        final TestEntry e2 = entry(rawActorId);
        repo.save(e1, DEFAULT_TENANT_ID);
        repo.save(e2, DEFAULT_TENANT_ID);

        final String token1 = em.find(LedgerEntry.class, e1.id).actorId;
        final String token2 = em.find(LedgerEntry.class, e2.id).actorId;
        assertThat(token1).isEqualTo(token2);
    }

    // ── Correctness: findByActorId for unknown actorId returns empty ──────────

    @Test
    @Transactional
    void findByActorId_unknownActorId_returnsEmpty() {
        final Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        final Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

        assertThat(repo.findByActorId("never-saved-" + UUID.randomUUID(), from, to, DEFAULT_TENANT_ID)).isEmpty();
    }

    // ── Happy path: findAttestationsByAttestorIdAndCapabilityTag uses tokenised attestorId ──

    @Test
    @Transactional
    void findByAttestorIdAndCapabilityTag_withPseudonymisation_findsTokenisedAttestation() {
        final String rawAttestorId = "attestor-" + UUID.randomUUID();
        final TestEntry entry = entry(rawAttestorId);
        repo.save(entry, DEFAULT_TENANT_ID);

        final LedgerAttestation att = new LedgerAttestation();
        att.ledgerEntryId = entry.id;
        att.subjectId = entry.subjectId;
        att.attestorId = rawAttestorId;
        att.attestorType = ActorType.HUMAN;
        att.verdict = AttestationVerdict.SOUND;
        att.confidence = 1.0;
        att.capabilityTag = "security-review";
        att.occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        repo.saveAttestation(att, DEFAULT_TENANT_ID);

        // Query by raw attestorId — tokeniseForQuery must translate to the stored token
        final var results = repo.findAttestationsByAttestorIdAndCapabilityTag(rawAttestorId, "security-review", DEFAULT_TENANT_ID);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).capabilityTag).isEqualTo("security-review");
    }

    // ── Fixture ───────────────────────────────────────────────────────────────

    private TestEntry entry(final String actorId) {
        return entry(actorId, ActorType.HUMAN);
    }

    private TestEntry entry(final String actorId, final ActorType actorType) {
        final TestEntry e = new TestEntry();
        e.subjectId = UUID.randomUUID();
        e.sequenceNumber = 1;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = actorId;
        e.actorType = actorType;
        e.actorRole = "Classifier";
        e.occurredAt = Instant.now();
        return e;
    }

    // ── SYSTEM actor exemption tests ──────────────────────────────────────────

    @Test
    @Transactional
    void save_systemActorId_storedRaw_notTokenised() {
        final String rawActorId = "system:health-check";
        final TestEntry entry = entry(rawActorId, ActorType.SYSTEM);
        repo.save(entry, DEFAULT_TENANT_ID);

        final LedgerEntry stored = em.find(LedgerEntry.class, entry.id);
        assertThat(stored.actorId).isEqualTo(rawActorId);
    }

    @Test
    @Transactional
    void saveAttestation_systemAttestor_storedRaw_notTokenised() {
        final String rawAttestorId = "system:compliance-engine";
        final TestEntry entry = entry("human-actor-" + UUID.randomUUID());
        repo.save(entry, DEFAULT_TENANT_ID);

        final LedgerAttestation att = new LedgerAttestation();
        att.id = UUID.randomUUID();
        att.ledgerEntryId = entry.id;
        att.subjectId = entry.subjectId;
        att.attestorId = rawAttestorId;
        att.attestorType = ActorType.SYSTEM;
        att.verdict = AttestationVerdict.SOUND;
        att.confidence = 1.0;
        att.occurredAt = Instant.now();
        repo.saveAttestation(att, DEFAULT_TENANT_ID);

        final LedgerAttestation stored = em.find(LedgerAttestation.class, att.id);
        assertThat(stored.attestorId).isEqualTo(rawAttestorId);
    }

    @Test
    @Transactional
    void findByActorId_systemActor_roundTrip_worksWithoutTokenisation() {
        final String rawActorId = "system:scheduler";
        final Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        final Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

        repo.save(entry(rawActorId, ActorType.SYSTEM), DEFAULT_TENANT_ID);

        final List<LedgerEntry> results = repo.findByActorId(rawActorId, from, to, DEFAULT_TENANT_ID);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).actorId).isEqualTo(rawActorId);
    }

    @Test
    @Transactional
    void outcomeRecorder_systemAttestor_storedRaw_underTokenisation() {
        final UUID subjectId = UUID.randomUUID();
        final var record = io.casehub.ledger.api.model.OutcomeRecord.of(
                "human-actor-" + UUID.randomUUID(), subjectId, "code-review",
                AttestationVerdict.SOUND, 0.9)
                .withAttestor("system:compliance-engine", ActorType.SYSTEM);

        outcomeRecorder.record(record);

        final var entries = repo.findBySubjectId(subjectId, DEFAULT_TENANT_ID);
        assertThat(entries).hasSize(1);
        final var attestations = repo.findAttestationsByEntryId(entries.get(0).id, DEFAULT_TENANT_ID);
        assertThat(attestations).hasSize(1);
        assertThat(attestations.get(0).attestorId).isEqualTo("system:compliance-engine");
    }
}

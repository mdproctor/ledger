package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.CapabilityTag;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.service.supplement.TestEntry;
import io.quarkus.test.junit.QuarkusTest;
import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;

/**
 * Integration tests for capability-scoped attestation queries (issue #60).
 *
 * <p>Covers: happy path, correctness/isolation, robustness, and backward compatibility.
 */
@QuarkusTest
class LedgerAttestationCapabilityIT {

    @Inject
    LedgerEntryRepository repo;

    // ── Happy path: specific capability tag stored and retrieved ──────────────

    @Test
    @Transactional
    void findByEntryIdAndCapabilityTag_matchingTag_returnsAttestation() {
        final TestEntry entry = savedEntry();
        repo.saveAttestation(attestation(entry.id, entry.subjectId, "security-review"), DEFAULT_TENANT_ID);

        final List<LedgerAttestation> results =
                repo.findAttestationsByEntryIdAndCapabilityTag(entry.id, "security-review", DEFAULT_TENANT_ID);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).capabilityTag).isEqualTo("security-review");
    }

    // ── Happy path: global attestation retrieved by global query ──────────────

    @Test
    @Transactional
    void save_globalCapabilityTag_retrievedByGlobalQuery() {
        final TestEntry entry = savedEntry();
        repo.saveAttestation(attestation(entry.id, entry.subjectId, CapabilityTag.GLOBAL), DEFAULT_TENANT_ID);

        final List<LedgerAttestation> results =
                repo.findAttestationsByEntryIdGlobal(entry.id, DEFAULT_TENANT_ID);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).capabilityTag).isEqualTo(CapabilityTag.GLOBAL);
    }

    // ── Correctness: capability query excludes global attestations ────────────

    @Test
    @Transactional
    void findByEntryIdAndCapabilityTag_doesNotReturnGlobal() {
        final TestEntry entry = savedEntry();
        repo.saveAttestation(attestation(entry.id, entry.subjectId, CapabilityTag.GLOBAL), DEFAULT_TENANT_ID);
        repo.saveAttestation(attestation(entry.id, entry.subjectId, "security-review"), DEFAULT_TENANT_ID);

        final List<LedgerAttestation> results =
                repo.findAttestationsByEntryIdAndCapabilityTag(entry.id, "security-review", DEFAULT_TENANT_ID);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).capabilityTag).isEqualTo("security-review");
    }

    // ── Correctness: global query excludes capability-specific attestations ───

    @Test
    @Transactional
    void findByEntryIdGlobal_doesNotReturnCapabilitySpecific() {
        final TestEntry entry = savedEntry();
        repo.saveAttestation(attestation(entry.id, entry.subjectId, CapabilityTag.GLOBAL), DEFAULT_TENANT_ID);
        repo.saveAttestation(attestation(entry.id, entry.subjectId, "security-review"), DEFAULT_TENANT_ID);

        final List<LedgerAttestation> results =
                repo.findAttestationsByEntryIdGlobal(entry.id, DEFAULT_TENANT_ID);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).capabilityTag).isEqualTo(CapabilityTag.GLOBAL);
    }

    // ── Correctness: capability query isolates across multiple tags ───────────

    @Test
    @Transactional
    void findByEntryIdAndCapabilityTag_isolatesFromOtherTags() {
        final TestEntry entry = savedEntry();
        repo.saveAttestation(attestation(entry.id, entry.subjectId, "security-review"), DEFAULT_TENANT_ID);
        repo.saveAttestation(attestation(entry.id, entry.subjectId, "architecture-review"), DEFAULT_TENANT_ID);

        final List<LedgerAttestation> results =
                repo.findAttestationsByEntryIdAndCapabilityTag(entry.id, "security-review", DEFAULT_TENANT_ID);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).capabilityTag).isEqualTo("security-review");
    }

    // ── Correctness: actor+capability query spans multiple entries ────────────

    @Test
    @Transactional
    void findByAttestorIdAndCapabilityTag_spansMultipleEntries() {
        final String attestorId = "peer-" + UUID.randomUUID();
        final TestEntry e1 = savedEntry();
        final TestEntry e2 = savedEntry();
        final TestEntry e3 = savedEntry();

        repo.saveAttestation(attestationBy(e1.id, e1.subjectId, attestorId, "security-review"), DEFAULT_TENANT_ID);
        repo.saveAttestation(attestationBy(e2.id, e2.subjectId, attestorId, "security-review"), DEFAULT_TENANT_ID);
        repo.saveAttestation(attestationBy(e3.id, e3.subjectId, attestorId, "architecture-review"), DEFAULT_TENANT_ID);

        final List<LedgerAttestation> results =
                repo.findAttestationsByAttestorIdAndCapabilityTag(attestorId, "security-review", DEFAULT_TENANT_ID);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(a -> "security-review".equals(a.capabilityTag));
        assertThat(results).extracting(a -> a.ledgerEntryId)
                .containsExactlyInAnyOrder(e1.id, e2.id);
    }

    // ── Robustness: no matching tag returns empty ─────────────────────────────

    @Test
    @Transactional
    void findByEntryIdAndCapabilityTag_noMatch_returnsEmpty() {
        final TestEntry entry = savedEntry();
        repo.saveAttestation(attestation(entry.id, entry.subjectId, "security-review"), DEFAULT_TENANT_ID);

        final List<LedgerAttestation> results =
                repo.findAttestationsByEntryIdAndCapabilityTag(entry.id, "nonexistent-capability", DEFAULT_TENANT_ID);

        assertThat(results).isEmpty();
    }

    @Test
    @Transactional
    void findByEntryIdGlobal_noGlobalAttestations_returnsEmpty() {
        final TestEntry entry = savedEntry();
        repo.saveAttestation(attestation(entry.id, entry.subjectId, "security-review"), DEFAULT_TENANT_ID);

        final List<LedgerAttestation> results =
                repo.findAttestationsByEntryIdGlobal(entry.id, DEFAULT_TENANT_ID);

        assertThat(results).isEmpty();
    }

    // ── Backward compatibility: unset capabilityTag defaults to global ────────

    @Test
    @Transactional
    void newAttestation_capabilityTagNotSet_defaultsToGlobalSentinel() {
        final TestEntry entry = savedEntry();

        final LedgerAttestation att = new LedgerAttestation();
        att.ledgerEntryId = entry.id;
        att.subjectId = entry.subjectId;
        att.attestorId = "auto-reviewer-" + UUID.randomUUID();
        att.attestorType = ActorType.AGENT;
        att.verdict = AttestationVerdict.SOUND;
        att.confidence = 1.0;
        att.occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        // capabilityTag intentionally NOT set — field default must be CapabilityTag.GLOBAL
        repo.saveAttestation(att, DEFAULT_TENANT_ID);

        final List<LedgerAttestation> results =
                repo.findAttestationsByEntryIdGlobal(entry.id, DEFAULT_TENANT_ID);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).capabilityTag).isEqualTo(CapabilityTag.GLOBAL);
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private TestEntry savedEntry() {
        final TestEntry entry = new TestEntry();
        entry.subjectId = UUID.randomUUID();
        entry.sequenceNumber = 1;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "actor-" + UUID.randomUUID();
        entry.actorType = ActorType.AGENT;
        entry.actorRole = "Classifier";
        entry.occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        repo.save(entry, DEFAULT_TENANT_ID);
        return entry;
    }

    private LedgerAttestation attestation(final UUID entryId, final UUID subjectId,
            final String capabilityTag) {
        return attestationBy(entryId, subjectId, "peer-reviewer-" + UUID.randomUUID(), capabilityTag);
    }

    private LedgerAttestation attestationBy(final UUID entryId, final UUID subjectId,
            final String attestorId, final String capabilityTag) {
        final LedgerAttestation att = new LedgerAttestation();
        att.ledgerEntryId = entryId;
        att.subjectId = subjectId;
        att.attestorId = attestorId;
        att.attestorType = ActorType.AGENT;
        att.verdict = AttestationVerdict.SOUND;
        att.confidence = 1.0;
        att.capabilityTag = capabilityTag;
        att.occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return att;
    }
}

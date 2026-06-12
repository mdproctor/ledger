package io.casehub.ledger.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.supplement.ComplianceSupplement;
import io.casehub.ledger.service.supplement.TestEntry;
import io.casehub.platform.api.identity.ActorType;

/**
 * Tests for {@link io.casehub.ledger.runtime.model.LedgerEntry#canonicalBytes()}
 * and {@link io.casehub.ledger.runtime.model.LedgerEntry#domainContentBytes()}.
 */
class CanonicalBytesTest {

    @Test
    void includesNewBaseFields() {
        final TestEntry entry = new TestEntry();
        entry.subjectId = UUID.randomUUID();
        entry.sequenceNumber = 5;
        entry.entryType = LedgerEntryType.COMMAND;
        entry.actorId = "test-actor";
        entry.actorRole = "Resolver";
        entry.occurredAt = Instant.parse("2026-06-11T10:00:00Z");
        entry.tenancyId = "tenant-123";
        entry.actorType = ActorType.AGENT;
        entry.causedByEntryId = UUID.randomUUID();

        final byte[] canonical = entry.canonicalBytes();
        final String canonicalStr = new String(canonical, StandardCharsets.UTF_8);

        // Verify all new fields appear in the canonical form
        assertTrue(canonicalStr.contains("tenant-123"), "tenancyId should appear");
        assertTrue(canonicalStr.contains("AGENT"), "actorType should appear");
        assertTrue(canonicalStr.contains(entry.causedByEntryId.toString()), "causedByEntryId should appear");
    }

    @Test
    void includesSupplementJson_whenPresent() {
        final TestEntry entry = new TestEntry();
        entry.subjectId = UUID.randomUUID();
        entry.sequenceNumber = 1;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "agent-1";
        entry.actorRole = "Reviewer";
        entry.occurredAt = Instant.parse("2026-06-11T10:00:00Z");
        entry.tenancyId = "default";
        entry.actorType = ActorType.AGENT;

        final ComplianceSupplement cs = new ComplianceSupplement();
        cs.rationale = "Test rationale for compliance";
        entry.attach(cs);

        final byte[] canonical = entry.canonicalBytes();
        final String canonicalStr = new String(canonical, StandardCharsets.UTF_8);

        // Verify the supplement JSON appears in canonical form
        assertTrue(canonicalStr.contains("Test rationale for compliance"), "rationale should appear in canonical bytes");
    }

    @Test
    void excludesSupplementJson_whenAbsent() {
        final TestEntry entry = new TestEntry();
        entry.subjectId = UUID.randomUUID();
        entry.sequenceNumber = 2;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "actor-2";
        entry.actorRole = "Creator";
        entry.occurredAt = Instant.parse("2026-06-11T10:00:00Z");
        entry.tenancyId = "default";
        entry.actorType = ActorType.HUMAN;

        final byte[] canonical = entry.canonicalBytes();
        final String canonicalStr = new String(canonical, StandardCharsets.UTF_8);

        // Count the pipe separators — should be exactly 8 (9 fields)
        final long pipeCount = canonicalStr.chars().filter(ch -> ch == '|').count();
        assertEquals(8, pipeCount, "Should have exactly 8 pipes (9 base fields) when no supplements");
    }

    @Test
    void isDeterministic() {
        final TestEntry entry = new TestEntry();
        entry.subjectId = UUID.randomUUID();
        entry.sequenceNumber = 3;
        entry.entryType = LedgerEntryType.COMMAND;
        entry.actorId = "actor-3";
        entry.actorRole = "Executor";
        entry.occurredAt = Instant.parse("2026-06-11T10:00:00Z");
        entry.tenancyId = "tenant-abc";
        entry.actorType = ActorType.SYSTEM;
        entry.causedByEntryId = UUID.randomUUID();

        final byte[] first = entry.canonicalBytes();
        final byte[] second = entry.canonicalBytes();

        assertArrayEquals(first, second, "Canonical bytes should be deterministic");
    }

    @Test
    void mutatingTenancyId_changesHash() throws Exception {
        final TestEntry entry = new TestEntry();
        entry.subjectId = UUID.randomUUID();
        entry.sequenceNumber = 4;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "actor-4";
        entry.actorRole = "Observer";
        entry.occurredAt = Instant.parse("2026-06-11T10:00:00Z");
        entry.tenancyId = "tenant-original";
        entry.actorType = ActorType.AGENT;

        final String hashBefore = sha256hex(entry.canonicalBytes());

        entry.tenancyId = "tenant-modified";

        final String hashAfter = sha256hex(entry.canonicalBytes());

        assertNotEquals(hashBefore, hashAfter, "Changing tenancyId should change the hash");
    }

    @Test
    void mutatingActorType_changesHash() throws Exception {
        final TestEntry entry = new TestEntry();
        entry.subjectId = UUID.randomUUID();
        entry.sequenceNumber = 5;
        entry.entryType = LedgerEntryType.COMMAND;
        entry.actorId = "actor-5";
        entry.actorRole = "Modifier";
        entry.occurredAt = Instant.parse("2026-06-11T10:00:00Z");
        entry.tenancyId = "default";
        entry.actorType = ActorType.HUMAN;

        final String hashBefore = sha256hex(entry.canonicalBytes());

        entry.actorType = ActorType.AGENT;

        final String hashAfter = sha256hex(entry.canonicalBytes());

        assertNotEquals(hashBefore, hashAfter, "Changing actorType should change the hash");
    }

    @Test
    void mutatingCausedByEntryId_changesHash() throws Exception {
        final TestEntry entry = new TestEntry();
        entry.subjectId = UUID.randomUUID();
        entry.sequenceNumber = 6;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "actor-6";
        entry.actorRole = "Linker";
        entry.occurredAt = Instant.parse("2026-06-11T10:00:00Z");
        entry.tenancyId = "default";
        entry.actorType = ActorType.SYSTEM;
        entry.causedByEntryId = UUID.randomUUID();

        final String hashBefore = sha256hex(entry.canonicalBytes());

        entry.causedByEntryId = UUID.randomUUID();

        final String hashAfter = sha256hex(entry.canonicalBytes());

        assertNotEquals(hashBefore, hashAfter, "Changing causedByEntryId should change the hash");
    }

    @Test
    void mutatingSupplementField_afterRefresh_changesHash() throws Exception {
        final TestEntry entry = new TestEntry();
        entry.subjectId = UUID.randomUUID();
        entry.sequenceNumber = 7;
        entry.entryType = LedgerEntryType.ATTESTATION;
        entry.actorId = "actor-7";
        entry.actorRole = "Attestor";
        entry.occurredAt = Instant.parse("2026-06-11T10:00:00Z");
        entry.tenancyId = "default";
        entry.actorType = ActorType.AGENT;

        final ComplianceSupplement cs = new ComplianceSupplement();
        cs.confidenceScore = 0.85;
        entry.attach(cs);

        final String hashBefore = sha256hex(entry.canonicalBytes());

        // Mutate supplement and refresh
        cs.confidenceScore = 0.95;
        entry.refreshSupplementJson();

        final String hashAfter = sha256hex(entry.canonicalBytes());

        assertNotEquals(hashBefore, hashAfter, "Changing supplement field and refreshing should change the hash");
    }

    @Test
    void domainContentBytes_defaultIsEmpty() {
        // Test via subclass that exposes the protected method
        final TestEntryWithDomainAccess entry = new TestEntryWithDomainAccess();
        final byte[] domainBytes = entry.exposeDomainContentBytes();

        assertNotNull(domainBytes, "domainContentBytes should not return null");
        assertEquals(0, domainBytes.length, "Default domainContentBytes should be empty");
    }

    /**
     * Test-only subclass that exposes the protected {@code domainContentBytes()} method.
     */
    private static class TestEntryWithDomainAccess extends TestEntry {
        public byte[] exposeDomainContentBytes() {
            return domainContentBytes();
        }
    }

    @Test
    void nullBaseFields_produceEmptyStringsInCanonical() {
        final TestEntry entry = new TestEntry();
        // Set only required fields, leave all nullable fields as null
        entry.subjectId = UUID.randomUUID();
        entry.sequenceNumber = 8;
        entry.entryType = LedgerEntryType.EVENT;
        entry.occurredAt = Instant.parse("2026-06-11T10:00:00Z");
        entry.tenancyId = "default";

        // These are all nullable:
        entry.actorId = null;
        entry.actorRole = null;
        entry.actorType = null;
        entry.causedByEntryId = null;

        // Should not throw NPE
        assertDoesNotThrow(() -> {
            final byte[] canonical = entry.canonicalBytes();
            assertNotNull(canonical, "Canonical bytes should not be null even with null fields");

            final String canonicalStr = new String(canonical, StandardCharsets.UTF_8);
            // Verify empty strings appear for null fields (multiple consecutive pipes)
            assertTrue(canonicalStr.contains("||"), "Null fields should produce empty strings (consecutive pipes)");
        }, "canonicalBytes should not throw NPE for null fields");
    }

    @Test
    void occurredAt_truncatedToMillis() {
        final TestEntry entry = new TestEntry();
        entry.subjectId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        entry.sequenceNumber = 1;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "actor-1";
        entry.actorRole = "Tester";
        entry.occurredAt = Instant.parse("2026-04-18T10:00:00.123456789Z");
        entry.tenancyId = "tenant-a";
        entry.actorType = ActorType.AGENT;

        final String canonical = new String(entry.canonicalBytes(), StandardCharsets.UTF_8);
        assertTrue(canonical.contains("2026-04-18T10:00:00.123Z"), "should contain millis-truncated timestamp");
        assertFalse(canonical.contains("123456789"), "should not contain nanoseconds");
    }

    @Test
    void domainContentBytes_nonEmpty_appearsInCanonical() {
        final TestEntry entry = new TestEntry() {
            @Override
            protected byte[] domainContentBytes() {
                return "custom-domain-data".getBytes(StandardCharsets.UTF_8);
            }
        };
        entry.subjectId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        entry.sequenceNumber = 1;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "actor-1";
        entry.actorRole = "Tester";
        entry.occurredAt = Instant.parse("2026-04-18T10:00:00Z");
        entry.tenancyId = "tenant-a";
        entry.actorType = ActorType.AGENT;

        final String canonical = new String(entry.canonicalBytes(), StandardCharsets.UTF_8);
        assertTrue(canonical.endsWith("|custom-domain-data"), "domain content bytes should appear at end");
    }

    // ── Test utilities ────────────────────────────────────────────────────────

    private static String sha256hex(final byte[] input) throws Exception {
        final MessageDigest md = MessageDigest.getInstance("SHA-256");
        final byte[] hash = md.digest(input);
        final StringBuilder sb = new StringBuilder(64);
        for (final byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

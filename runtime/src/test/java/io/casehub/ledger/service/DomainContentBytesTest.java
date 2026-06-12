package io.casehub.ledger.service;

import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.ActorIdentityBindingEntry;
import io.casehub.ledger.runtime.model.KeyRotationEntry;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.CredentialValidationResult;
import io.casehub.platform.api.identity.IdentityBindingStatus;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for domain content bytes in LedgerEntry subclasses.
 * Domain content is included in canonicalBytes() alongside core fields.
 *
 * Tests verify domain content via canonicalBytes() — when domain fields change,
 * canonicalBytes() should change. This ensures domain content is properly included
 * in the Merkle tree hash.
 */
class DomainContentBytesTest {

    @Test
    void plainLedgerEntry_canonicalBytesWithoutDomainContent() {
        // Plain LedgerEntry has no domain-specific fields — verify it compiles
        LedgerEntry entry = new LedgerEntry() {
            // concrete subclass for test
        };
        entry.subjectId = UUID.randomUUID();
        entry.sequenceNumber = 1;
        entry.entryType = LedgerEntryType.COMMAND;
        entry.actorId = "test-actor";
        entry.actorRole = "USER";
        entry.occurredAt = Instant.now();
        entry.tenancyId = "tenant-1";
        entry.actorType = ActorType.HUMAN;

        byte[] canonical = entry.canonicalBytes();

        assertNotNull(canonical);
        assertTrue(canonical.length > 0, "Canonical bytes should include core fields");
    }

    @Test
    void keyRotationEntry_includesAllFields() {
        // Set only domain fields to verify they appear in canonical bytes
        KeyRotationEntry entry = new KeyRotationEntry();
        entry.subjectId = UUID.randomUUID();
        entry.sequenceNumber = 1;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "agent-1";
        entry.actorRole = "SYSTEM";
        entry.occurredAt = Instant.parse("2026-06-11T10:00:00Z");
        entry.tenancyId = "tenant-1";
        entry.actorType = ActorType.AGENT;
        entry.previousKeyRef = "prev-key-ref";
        entry.newKeyRef = "new-key-ref";
        entry.reason = KeyRotationReason.SCHEDULED;
        entry.effectiveSince = Instant.parse("2026-06-11T09:00:00Z");

        byte[] canonical = entry.canonicalBytes();
        String canonicalString = new String(canonical, StandardCharsets.UTF_8);

        // Verify domain fields appear in canonical bytes
        assertTrue(canonicalString.contains("prev-key-ref"), "Should include previousKeyRef");
        assertTrue(canonicalString.contains("new-key-ref"), "Should include newKeyRef");
        assertTrue(canonicalString.contains("SCHEDULED"), "Should include reason enum name");
        assertTrue(canonicalString.contains("2026-06-11T09:00:00Z"), "Should include effectiveSince");
    }

    @Test
    void keyRotationEntry_mutatingReason_changesCanonicalBytes() {
        UUID subjectId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-06-11T10:00:00Z");
        Instant effectiveSince = Instant.parse("2026-06-11T09:00:00Z");

        // Create fully populated entry with SCHEDULED reason
        KeyRotationEntry entry = new KeyRotationEntry();
        entry.subjectId = subjectId;
        entry.sequenceNumber = 1;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "agent-1";
        entry.actorRole = "SYSTEM";
        entry.occurredAt = occurredAt;
        entry.tenancyId = "tenant-1";
        entry.actorType = ActorType.AGENT;
        entry.previousKeyRef = "prev-key";
        entry.newKeyRef = "new-key";
        entry.reason = KeyRotationReason.SCHEDULED;
        entry.effectiveSince = effectiveSince;

        byte[] canonicalBytes1 = entry.canonicalBytes();

        // Mutate only the reason
        entry.reason = KeyRotationReason.COMPROMISED;

        byte[] canonicalBytes2 = entry.canonicalBytes();

        // Canonical bytes should differ because domain content changed
        assertFalse(
            java.util.Arrays.equals(canonicalBytes1, canonicalBytes2),
            "Changing reason should change canonicalBytes"
        );
    }

    @Test
    void identityBindingEntry_includesAllFields() {
        ActorIdentityBindingEntry entry = new ActorIdentityBindingEntry();
        entry.subjectId = UUID.randomUUID();
        entry.sequenceNumber = 1;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "agent-1";
        entry.actorRole = "SYSTEM";
        entry.occurredAt = Instant.parse("2026-06-11T10:00:00Z");
        entry.tenancyId = "tenant-1";
        entry.actorType = ActorType.AGENT;
        entry.boundDid = "did:web:example.com:agent";
        entry.validationResult = IdentityBindingStatus.VALID;
        entry.alsoKnownAsVerified = true;
        entry.keyMatchVerified = true;
        entry.verifiedKeyRef = "verified-key-ref";
        entry.credentialResult = CredentialValidationResult.VALID;
        entry.didMethod = "web";

        byte[] canonical = entry.canonicalBytes();
        String canonicalString = new String(canonical, StandardCharsets.UTF_8);

        // Verify domain fields appear in canonical bytes
        assertTrue(canonicalString.contains("did:web:example.com:agent"), "Should include boundDid");
        assertTrue(canonicalString.contains("VALID"), "Should include validationResult enum name");
        assertTrue(canonicalString.contains("true"), "Should include alsoKnownAsVerified");
        assertTrue(canonicalString.contains("verified-key-ref"), "Should include verifiedKeyRef");
        assertTrue(canonicalString.contains("VALID"), "Should include credentialResult enum name");
        assertTrue(canonicalString.contains("web"), "Should include didMethod");
    }

    @Test
    void identityBindingEntry_mutatingBoundDid_changesCanonicalBytes() {
        UUID subjectId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-06-11T10:00:00Z");

        // Create fully populated entry
        ActorIdentityBindingEntry entry = new ActorIdentityBindingEntry();
        entry.subjectId = subjectId;
        entry.sequenceNumber = 1;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "agent-1";
        entry.actorRole = "SYSTEM";
        entry.occurredAt = occurredAt;
        entry.tenancyId = "tenant-1";
        entry.actorType = ActorType.AGENT;
        entry.boundDid = "did:web:example.com:agent1";
        entry.validationResult = IdentityBindingStatus.VALID;
        entry.alsoKnownAsVerified = true;
        entry.keyMatchVerified = true;
        entry.verifiedKeyRef = "key-ref-1";
        entry.credentialResult = CredentialValidationResult.VALID;
        entry.didMethod = "web";

        byte[] canonicalBytes1 = entry.canonicalBytes();

        // Mutate only the boundDid
        entry.boundDid = "did:web:example.com:agent2";

        byte[] canonicalBytes2 = entry.canonicalBytes();

        // Canonical bytes should differ because domain content changed
        assertFalse(
            java.util.Arrays.equals(canonicalBytes1, canonicalBytes2),
            "Changing boundDid should change canonicalBytes"
        );
    }
}

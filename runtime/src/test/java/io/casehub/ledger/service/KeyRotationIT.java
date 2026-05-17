package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.ActorType;
import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.KeyRotationEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.AgentKeyProvider;
import io.casehub.ledger.runtime.service.KeyRotationService;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.ledger.runtime.service.SigningKey;
import io.casehub.ledger.runtime.service.model.VerificationResult;
import io.casehub.ledger.service.supplement.TestEntry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class KeyRotationIT {

    @Inject LedgerEntryRepository repo;
    @Inject LedgerVerificationService verificationService;
    @Inject KeyRotationService rotationService;

    @InjectMock
    AgentKeyProvider agentKeyProvider;

    private SigningKey currentKey;
    private SigningKey nextKey;

    @BeforeEach
    void setUp() throws Exception {
        final KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
        currentKey = SigningKey.of(gen.generateKeyPair());
        nextKey = SigningKey.of(gen.generateKeyPair());

        when(agentKeyProvider.signingKey(anyString())).thenReturn(Optional.empty());
        when(agentKeyProvider.signingKey("claude:reviewer@v1"))
                .thenReturn(Optional.of(currentKey));
    }

    private TestEntry seedSigned(final UUID subjectId, final int seq) {
        final TestEntry e = new TestEntry();
        e.subjectId = subjectId;
        e.sequenceNumber = seq;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = "claude:reviewer@v1";
        e.actorType = ActorType.AGENT;
        e.actorRole = "Reviewer";
        return (TestEntry) repo.save(e);
    }

    @Test
    @Transactional
    void scheduledRotation_existingEntries_remainValid() {
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedSigned(sub, 1);

        assertThat(e.agentKeyRef).isEqualTo(currentKey.keyRef());
        assertThat(verificationService.verifyAgentSignature(e.id)).isEqualTo(VerificationResult.VALID);

        rotationService.recordRotation("claude:reviewer@v1",
                currentKey.keyRef(), nextKey.keyRef(),
                KeyRotationReason.SCHEDULED, Instant.now());

        assertThat(verificationService.verifyAgentSignature(e.id)).isEqualTo(VerificationResult.VALID);
    }

    @Test
    @Transactional
    void compromisedRotation_entryAfterEffectiveSince_isSuspect() {
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedSigned(sub, 1);

        assertThat(verificationService.verifyAgentSignature(e.id)).isEqualTo(VerificationResult.VALID);

        rotationService.recordRotation("claude:reviewer@v1",
                currentKey.keyRef(), null,
                KeyRotationReason.COMPROMISED,
                e.occurredAt.minusSeconds(60));

        assertThat(verificationService.verifyAgentSignature(e.id)).isEqualTo(VerificationResult.SUSPECT);
    }

    @Test
    @Transactional
    void rotationHistory_recordsAllEvents() {
        rotationService.recordRotation("claude:reviewer@v1",
                currentKey.keyRef(), nextKey.keyRef(),
                KeyRotationReason.SCHEDULED, Instant.now());
        rotationService.recordRotation("claude:reviewer@v1",
                nextKey.keyRef(), null,
                KeyRotationReason.COMPROMISED, Instant.now());

        final List<KeyRotationEntry> history =
                rotationService.rotationHistory("claude:reviewer@v1");

        assertThat(history).hasSizeGreaterThanOrEqualTo(2);
        assertThat(history.stream().map(e -> e.reason))
                .contains(KeyRotationReason.SCHEDULED, KeyRotationReason.COMPROMISED);
    }

    @Test
    @Transactional
    void keyRotationEntry_subjectId_isDeterministic() {
        final KeyRotationEntry entry = rotationService.recordRotation(
                "claude:reviewer@v1", currentKey.keyRef(), nextKey.keyRef(),
                KeyRotationReason.SCHEDULED, Instant.now());

        final UUID expected = UUID.nameUUIDFromBytes(
                "claude:reviewer@v1".getBytes(StandardCharsets.UTF_8));
        assertThat(entry.subjectId).isEqualTo(expected);
    }
}

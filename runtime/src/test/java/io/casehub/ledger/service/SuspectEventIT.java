package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.ActorType;
import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.AgentKeyProvider;
import io.casehub.ledger.runtime.service.AgentSignatureSuspectEvent;
import io.casehub.ledger.runtime.service.KeyRotationService;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.ledger.runtime.service.ReactiveLedgerVerificationService;
import io.casehub.ledger.runtime.service.SigningKey;
import io.casehub.ledger.runtime.service.model.VerificationResult;
import io.casehub.ledger.service.supplement.TestEntry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

/**
 * End-to-end integration tests for {@link AgentSignatureSuspectEvent} firing.
 *
 * <p>
 * {@code @Transactional} on each test method rolls back DB state automatically.
 * An isolated DB profile is not required — {@link AgentSuspectEventCapture#reset()}
 * handles CDI bean state isolation between tests.
 */
@QuarkusTest
class SuspectEventIT {

    @Inject LedgerEntryRepository repo;
    @Inject LedgerVerificationService verificationService;
    @Inject ReactiveLedgerVerificationService reactiveVerificationService;
    @Inject KeyRotationService rotationService;
    @Inject AgentSuspectEventCapture eventCapture;

    @InjectMock
    AgentKeyProvider agentKeyProvider;

    private SigningKey testKey;

    @BeforeEach
    void setUp() throws Exception {
        testKey = SigningKey.of(KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
        when(agentKeyProvider.signingKey(anyString())).thenReturn(Optional.empty());
        when(agentKeyProvider.signingKey("claude:reviewer@v1"))
                .thenReturn(Optional.of(testKey));
        eventCapture.reset();
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
    void verifyAgentSignature_suspect_firesSyncEvent() {
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedSigned(sub, 1);

        rotationService.recordRotation("claude:reviewer@v1", testKey.keyRef(), null,
                KeyRotationReason.COMPROMISED, e.occurredAt.minusSeconds(60));

        final VerificationResult result = verificationService.verifyAgentSignature(e.id);

        assertThat(result).isEqualTo(VerificationResult.SUSPECT);
        assertThat(eventCapture.syncEvents()).hasSize(1);
        final AgentSignatureSuspectEvent event = eventCapture.syncEvents().get(0);
        assertThat(event.entryId()).isEqualTo(e.id);
        assertThat(event.actorId()).isEqualTo("claude:reviewer@v1");
        assertThat(event.keyRef()).isEqualTo(testKey.keyRef());
    }

    @Test
    @Transactional
    void verifyAgentSignatureAsync_unsigned_returnsUnsigned() {
        final TestEntry e = new TestEntry();
        e.subjectId = UUID.randomUUID();
        e.sequenceNumber = 1;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = "system:noop";
        e.actorType = ActorType.SYSTEM;
        e.actorRole = "System";
        final TestEntry saved = (TestEntry) repo.save(e);

        final VerificationResult result =
                reactiveVerificationService.verifyAgentSignatureAsync(saved.id).await().indefinitely();

        assertThat(result).isEqualTo(VerificationResult.UNSIGNED);
        assertThat(eventCapture.syncEvents()).isEmpty();
    }

    @Test
    @Transactional
    void verifyAgentSignatureAsync_valid_returnsValid() {
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedSigned(sub, 1);

        final VerificationResult result =
                reactiveVerificationService.verifyAgentSignatureAsync(e.id).await().indefinitely();

        assertThat(result).isEqualTo(VerificationResult.VALID);
        assertThat(eventCapture.syncEvents()).isEmpty();
    }

    @Test
    @Transactional
    void verifyAgentSignatureAsync_suspect_firesAsyncEvent() throws Exception {
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedSigned(sub, 1);

        rotationService.recordRotation("claude:reviewer@v1", testKey.keyRef(), null,
                KeyRotationReason.COMPROMISED, e.occurredAt.minusSeconds(60));

        final VerificationResult result =
                reactiveVerificationService.verifyAgentSignatureAsync(e.id).await().indefinitely();

        assertThat(result).isEqualTo(VerificationResult.SUSPECT);

        final boolean received = eventCapture.asyncLatch().await(5, TimeUnit.SECONDS);
        assertThat(received).as("async event must be received within 5 seconds").isTrue();

        final AgentSignatureSuspectEvent event = eventCapture.lastAsyncEvent();
        assertThat(event).isNotNull();
        assertThat(event.entryId()).isEqualTo(e.id);
        assertThat(event.actorId()).isEqualTo("claude:reviewer@v1");
        assertThat(event.keyRef()).isEqualTo(testKey.keyRef());
        assertThat(event.occurredAt()).isEqualTo(e.occurredAt);
        assertThat(event.effectiveSince()).isNotNull().isBefore(e.occurredAt.plusSeconds(1));
    }

    @Test
    @Transactional
    void verifyAgentSignatureAsync_invalid_returnsInvalid() {
        // Build the entry with a fresh tampered signature array — avoids mutating a
        // Hibernate-cached object whose in-place mutation could bleed across tests.
        final TestEntry e = new TestEntry();
        e.subjectId = UUID.randomUUID();
        e.sequenceNumber = 1;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = "claude:reviewer@v1";
        e.actorType = ActorType.AGENT;
        e.actorRole = "Reviewer";
        final TestEntry saved = (TestEntry) repo.save(e);

        final byte[] tamperedSignature = new byte[64];
        tamperedSignature[0] = (byte) 0xFF;
        saved.agentSignature = tamperedSignature;
        saved.agentPublicKey = testKey.keyPair().getPublic().getEncoded();
        saved.agentKeyRef = testKey.keyRef();
        repo.save(saved);

        final VerificationResult result =
                reactiveVerificationService.verifyAgentSignatureAsync(saved.id).await().indefinitely();

        assertThat(result).isEqualTo(VerificationResult.INVALID);
        assertThat(eventCapture.syncEvents()).isEmpty();
    }
}

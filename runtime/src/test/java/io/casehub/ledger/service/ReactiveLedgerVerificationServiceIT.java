package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.ActorType;
import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.AgentSignatureSuspectEvent;
import io.casehub.ledger.runtime.service.KeyRotationService;
import io.casehub.ledger.runtime.service.LedgerMerkleTree;
import io.casehub.ledger.runtime.service.ReactiveLedgerVerificationService;
import io.casehub.ledger.runtime.service.SigningKey;
import io.casehub.ledger.runtime.service.model.VerificationResult;
import io.casehub.ledger.service.supplement.TestEntry;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests for {@link ReactiveLedgerVerificationService}.
 *
 * <p>
 * {@code @Transactional} on each test method is required for the <em>setup</em>
 * operations that use the blocking {@link io.casehub.ledger.runtime.repository.LedgerEntryRepository}
 * and {@link KeyRotationService}. The reactive verification calls under test go
 * through {@link BlockingReactiveLedgerEntryRepository}, which delegates to the
 * blocking repo on the same thread and therefore participates in the same JTA
 * transaction. In production the reactive path runs outside any JTA context.
 */
@QuarkusTest
class ReactiveLedgerVerificationServiceIT {

    @Inject
    ReactiveLedgerVerificationService reactiveVerificationService;

    @Inject
    LedgerEntryRepository repo;

    @Inject
    KeyRotationService rotationService;

    @Inject
    AgentSuspectEventCapture eventCapture;

    private TestEntry seedEntry(final UUID subjectId, final int seq, final String actorId) {
        final TestEntry e = new TestEntry();
        e.subjectId = subjectId;
        e.sequenceNumber = seq;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = actorId;
        e.actorType = ActorType.SYSTEM;
        e.actorRole = "Tester";
        return (TestEntry) repo.save(e);
    }

    @Test
    @Transactional
    void verifyAgentSignatureAsync_unsignedEntry_returnsUnsigned() {
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, 1, "unsigned-actor");

        final VerificationResult result = reactiveVerificationService
                .verifyAgentSignatureAsync(e.id)
                .await().atMost(Duration.ofSeconds(5));

        assertThat(result).isEqualTo(VerificationResult.UNSIGNED);
    }

    @Test
    @Transactional
    void verifyAgentSignatureAsync_validSignature_returnsValid() throws Exception {
        final java.security.KeyPairGenerator gen = java.security.KeyPairGenerator.getInstance("Ed25519");
        final SigningKey sk = SigningKey.of(gen.generateKeyPair());
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, 1, "signed-actor");
        final byte[] canonical = LedgerMerkleTree.canonicalBytes(e);
        final java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
        sig.initSign(sk.keyPair().getPrivate());
        sig.update(canonical);
        e.agentSignature = sig.sign();
        e.agentPublicKey = sk.keyPair().getPublic().getEncoded();
        e.agentKeyRef = sk.keyRef();
        repo.save(e);

        final VerificationResult result = reactiveVerificationService
                .verifyAgentSignatureAsync(e.id)
                .await().atMost(Duration.ofSeconds(5));

        assertThat(result).isEqualTo(VerificationResult.VALID);
    }

    @Test
    @Transactional
    void verifyAgentSignatureAsync_tamperedSignature_returnsInvalid() throws Exception {
        final java.security.KeyPairGenerator gen = java.security.KeyPairGenerator.getInstance("Ed25519");
        final SigningKey sk = SigningKey.of(gen.generateKeyPair());
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, 1, "signed-actor");
        final byte[] canonical = LedgerMerkleTree.canonicalBytes(e);
        final java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
        sig.initSign(sk.keyPair().getPrivate());
        sig.update(canonical);
        final byte[] signature = sig.sign();
        signature[0] ^= 0xFF;
        e.agentSignature = signature;
        e.agentPublicKey = sk.keyPair().getPublic().getEncoded();
        e.agentKeyRef = sk.keyRef();
        repo.save(e);

        final VerificationResult result = reactiveVerificationService
                .verifyAgentSignatureAsync(e.id)
                .await().atMost(Duration.ofSeconds(5));

        assertThat(result).isEqualTo(VerificationResult.INVALID);
    }

    @Test
    @Transactional
    void verifyAgentSignatureAsync_suspectEntry_firesEventViaReactivePath() throws Exception {
        eventCapture.reset();
        final java.security.KeyPairGenerator gen = java.security.KeyPairGenerator.getInstance("Ed25519");
        final SigningKey sk = SigningKey.of(gen.generateKeyPair());
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, 1, "claude:reviewer@v1");
        final byte[] canonical = LedgerMerkleTree.canonicalBytes(e);
        final java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
        sig.initSign(sk.keyPair().getPrivate());
        sig.update(canonical);
        e.agentSignature = sig.sign();
        e.agentPublicKey = sk.keyPair().getPublic().getEncoded();
        e.agentKeyRef = sk.keyRef();
        repo.save(e);

        final java.time.Instant compromisedSince = e.occurredAt.minusSeconds(60);
        rotationService.recordRotation("claude:reviewer@v1", sk.keyRef(), null,
                KeyRotationReason.COMPROMISED, compromisedSince);

        final VerificationResult result = reactiveVerificationService
                .verifyAgentSignatureAsync(e.id)
                .await().atMost(Duration.ofSeconds(5));

        assertThat(result).isEqualTo(VerificationResult.SUSPECT);
        assertThat(eventCapture.asyncLatch().await(5, TimeUnit.SECONDS)).isTrue();
        final AgentSignatureSuspectEvent event = eventCapture.lastAsyncEvent();
        assertThat(event.entryId()).isEqualTo(e.id);
        assertThat(event.effectiveSince()).isEqualTo(compromisedSince);
    }
}

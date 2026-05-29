package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.AgentSignature;
import io.casehub.ledger.runtime.service.AgentSignatureVerificationService;
import io.casehub.ledger.runtime.service.AgentSignatureSuspectEvent;
import io.casehub.ledger.runtime.service.KeyRotationService;
import io.casehub.ledger.runtime.service.LedgerMerkleTree;
import io.casehub.ledger.runtime.service.model.VerificationResult;
import io.casehub.ledger.service.supplement.TestEntry;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AgentSignatureVerificationServiceIT {

    @Inject
    AgentSignatureVerificationService signatureService;
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

    private static AgentSignature signEntry(final TestEntry e, final KeyPair kp) {
        final AgentSignature sig = AgentSignature.signWith(kp, LedgerMerkleTree.canonicalBytes(e));
        e.agentSignature = sig.signature();
        e.agentPublicKey = sig.publicKey();
        e.agentKeyRef = sig.keyRef();
        return sig;
    }

    @Test
    @Transactional
    void verifyAgentSignature_unsignedEntry_returnsUnsigned() {
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, 1, "unsigned-actor");

        assertThat(signatureService.verifyAgentSignature(e.id))
                .isEqualTo(VerificationResult.UNSIGNED);
    }

    @Test
    @Transactional
    void verifyAgentSignature_validSignature_returnsValid() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, 1, "signed-actor");
        signEntry(e, kp);
        repo.save(e);

        assertThat(signatureService.verifyAgentSignature(e.id))
                .isEqualTo(VerificationResult.VALID);
    }

    @Test
    @Transactional
    void verifyAgentSignature_tamperedSignature_returnsInvalid() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, 1, "signed-actor");
        signEntry(e, kp);
        e.agentSignature[0] ^= 0xFF;
        repo.save(e);

        assertThat(signatureService.verifyAgentSignature(e.id))
                .isEqualTo(VerificationResult.INVALID);
    }

    @Test
    @Transactional
    void verifyAgentSignature_unknownEntry_throwsIllegalArgument() {
        final UUID nonexistent = UUID.randomUUID();
        assertThatThrownBy(() -> signatureService.verifyAgentSignature(nonexistent))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Transactional
    void verifyAgentSignature_mutatedActorId_returnsInvalid() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, 1, "original-actor");
        signEntry(e, kp);
        e.actorId = "impersonator-actor";
        repo.save(e);

        assertThat(signatureService.verifyAgentSignature(e.id))
                .isEqualTo(VerificationResult.INVALID);
    }

    @Test
    @Transactional
    void verifyAgentSignature_compromisedKey_afterEffectiveSince_returnsSuspect() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, 1, "claude:reviewer@v1");
        final AgentSignature sig = signEntry(e, kp);
        repo.save(e);

        final Instant compromisedSince = e.occurredAt.minusSeconds(60);
        rotationService.recordRotation("claude:reviewer@v1", sig.keyRef(), null,
                KeyRotationReason.COMPROMISED, compromisedSince);

        assertThat(signatureService.verifyAgentSignature(e.id))
                .isEqualTo(VerificationResult.SUSPECT);
    }

    @Test
    @Transactional
    void verifyAgentSignature_compromisedKey_beforeEffectiveSince_returnsValid() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, 1, "claude:reviewer@v1");
        final AgentSignature sig = signEntry(e, kp);
        repo.save(e);

        final Instant compromisedSince = e.occurredAt.plusSeconds(3600);
        rotationService.recordRotation("claude:reviewer@v1", sig.keyRef(), null,
                KeyRotationReason.COMPROMISED, compromisedSince);

        assertThat(signatureService.verifyAgentSignature(e.id))
                .isEqualTo(VerificationResult.VALID);
    }

    @Test
    @Transactional
    void verifyAgentSignature_scheduledRotation_doesNotProduceSuspect() throws Exception {
        final KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
        final KeyPair kp = gen.generateKeyPair();
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, 1, "claude:reviewer@v1");
        final AgentSignature sig = signEntry(e, kp);
        repo.save(e);

        final String newKeyRef = AgentSignature.signWith(gen.generateKeyPair(), new byte[0]).keyRef();
        rotationService.recordRotation("claude:reviewer@v1", sig.keyRef(),
                newKeyRef,
                KeyRotationReason.SCHEDULED, e.occurredAt.minusSeconds(60));

        assertThat(signatureService.verifyAgentSignature(e.id))
                .isEqualTo(VerificationResult.VALID);
    }

    @Test
    @Transactional
    // @Transactional is intentional: CDI synchronous Event.fire() delivers within the same call
    // so observers run before verifyAgentSignature() returns. If delivery ever moves to
    // @TransactionPhase.AFTER_SUCCESS, remove @Transactional here so the service commits first.
    void verifyAgentSignature_suspect_firesSuspectEvent() throws Exception {
        eventCapture.reset();
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, 1, "claude:reviewer@v1");
        final AgentSignature sig = signEntry(e, kp);
        repo.save(e);

        final Instant compromisedSince = e.occurredAt.minusSeconds(60);
        rotationService.recordRotation("claude:reviewer@v1", sig.keyRef(), null,
                KeyRotationReason.COMPROMISED, compromisedSince);

        signatureService.verifyAgentSignature(e.id);

        assertThat(eventCapture.syncEvents()).hasSize(1);
        final AgentSignatureSuspectEvent event = eventCapture.syncEvents().get(0);
        assertThat(event.entryId()).isEqualTo(e.id);
        assertThat(event.actorId()).isEqualTo("claude:reviewer@v1");
        assertThat(event.keyRef()).isEqualTo(sig.keyRef());
        assertThat(event.occurredAt()).isEqualTo(e.occurredAt);
        assertThat(event.effectiveSince()).isEqualTo(compromisedSince);
    }

    @Test
    @Transactional
    void verifyAgentSignature_valid_doesNotFireEvent() throws Exception {
        eventCapture.reset();
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, 1, "claude:reviewer@v1");
        signEntry(e, kp);
        repo.save(e);

        signatureService.verifyAgentSignature(e.id);

        assertThat(eventCapture.syncEvents()).isEmpty();
    }

    @Test
    @Transactional
    void verifyAgentSignature_invalid_doesNotFireEvent() throws Exception {
        eventCapture.reset();
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, 1, "claude:reviewer@v1");
        signEntry(e, kp);
        e.agentSignature[0] ^= 0xFF;
        repo.save(e);

        signatureService.verifyAgentSignature(e.id);

        assertThat(eventCapture.syncEvents()).isEmpty();
    }
}

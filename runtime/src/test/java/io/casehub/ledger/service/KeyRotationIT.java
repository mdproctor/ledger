package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.KeyRotationEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.AgentSignature;
import io.casehub.ledger.runtime.service.AgentSigner;
import io.casehub.ledger.runtime.service.KeyRotationService;
import io.casehub.ledger.runtime.service.AgentSignatureVerificationService;
import io.casehub.ledger.runtime.service.model.VerificationResult;
import io.casehub.ledger.service.supplement.TestEntry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;

@QuarkusTest
class KeyRotationIT {

    @Inject LedgerEntryRepository repo;
    @Inject AgentSignatureVerificationService verificationService;
    @Inject KeyRotationService rotationService;

    @InjectMock
    AgentSigner agentSigner;

    private KeyPair currentKeyPair;
    private KeyPair nextKeyPair;
    private String currentKeyRef;
    private String nextKeyRef;

    @BeforeEach
    void setUp() throws Exception {
        final KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
        currentKeyPair = gen.generateKeyPair();
        nextKeyPair = gen.generateKeyPair();
        currentKeyRef = AgentSignature.signWith(currentKeyPair, new byte[0]).keyRef();
        nextKeyRef = AgentSignature.signWith(nextKeyPair, new byte[0]).keyRef();

        when(agentSigner.sign(anyString(), any())).thenReturn(Optional.empty());
        when(agentSigner.sign(eq("claude:reviewer@v1"), any()))
                .thenAnswer(inv -> Optional.of(
                        AgentSignature.signWith(currentKeyPair, inv.getArgument(1))));
    }

    private TestEntry seedSigned(final UUID subjectId, final int seq) {
        final TestEntry e = new TestEntry();
        e.subjectId = subjectId;
        e.sequenceNumber = seq;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = "claude:reviewer@v1";
        e.actorType = ActorType.AGENT;
        e.actorRole = "Reviewer";
        return (TestEntry) repo.save(e, DEFAULT_TENANT_ID);
    }

    @Test
    @Transactional
    void scheduledRotation_existingEntries_remainValid() {
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedSigned(sub, 1);

        assertThat(e.agentKeyRef).isEqualTo(currentKeyRef);
        assertThat(verificationService.verifyAgentSignature(e.id, DEFAULT_TENANT_ID)).isEqualTo(VerificationResult.VALID);

        rotationService.recordRotation("claude:reviewer@v1",
                currentKeyRef, nextKeyRef,
                KeyRotationReason.SCHEDULED, Instant.now(), DEFAULT_TENANT_ID);

        assertThat(verificationService.verifyAgentSignature(e.id, DEFAULT_TENANT_ID)).isEqualTo(VerificationResult.VALID);
    }

    @Test
    @Transactional
    void compromisedRotation_entryAfterEffectiveSince_isSuspect() {
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedSigned(sub, 1);

        assertThat(verificationService.verifyAgentSignature(e.id, DEFAULT_TENANT_ID)).isEqualTo(VerificationResult.VALID);

        rotationService.recordRotation("claude:reviewer@v1",
                currentKeyRef, null,
                KeyRotationReason.COMPROMISED,
                e.occurredAt.minusSeconds(60), DEFAULT_TENANT_ID);

        assertThat(verificationService.verifyAgentSignature(e.id, DEFAULT_TENANT_ID)).isEqualTo(VerificationResult.SUSPECT);
    }

    @Test
    @Transactional
    void rotationHistory_recordsAllEvents() {
        final String actorId = "claude:reviewer@rotation-history-" + UUID.randomUUID();
        rotationService.recordRotation(actorId,
                currentKeyRef, nextKeyRef,
                KeyRotationReason.SCHEDULED, Instant.now(), DEFAULT_TENANT_ID);
        rotationService.recordRotation(actorId,
                nextKeyRef, null,
                KeyRotationReason.COMPROMISED, Instant.now(), DEFAULT_TENANT_ID);

        final List<KeyRotationEntry> history = rotationService.rotationHistory(actorId);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).reason).isEqualTo(KeyRotationReason.SCHEDULED);
        assertThat(history.get(1).reason).isEqualTo(KeyRotationReason.COMPROMISED);
    }

    @Test
    @Transactional
    void keyRotationEntry_subjectId_isDeterministic() {
        final KeyRotationEntry entry = rotationService.recordRotation(
                "claude:reviewer@v1", currentKeyRef, nextKeyRef,
                KeyRotationReason.SCHEDULED, Instant.now(), DEFAULT_TENANT_ID);

        final UUID expected = UUID.nameUUIDFromBytes(
                "claude:reviewer@v1".getBytes(StandardCharsets.UTF_8));
        assertThat(entry.subjectId).isEqualTo(expected);
    }
}

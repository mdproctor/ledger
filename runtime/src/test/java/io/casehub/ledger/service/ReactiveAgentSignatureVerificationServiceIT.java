package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.AgentSignature;
import io.casehub.ledger.runtime.service.AgentSignatureSuspectEvent;
import io.casehub.ledger.runtime.service.AgentSigner;
import io.casehub.ledger.runtime.service.KeyRotationService;
import io.casehub.ledger.runtime.service.LedgerMerkleTree;
import io.casehub.ledger.runtime.service.ReactiveAgentSignatureVerificationService;
import io.casehub.ledger.runtime.service.model.VerificationResult;
import io.casehub.ledger.service.supplement.TestEntry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;

/**
 * Integration tests for {@link ReactiveAgentSignatureVerificationService}.
 *
 * <p>
 * {@code @Transactional} on each test method is required for the setup operations
 * that use the blocking {@link LedgerEntryRepository} and {@link KeyRotationService}.
 * The reactive verification calls go through {@link BlockingReactiveLedgerEntryRepository},
 * which delegates to the blocking repo on the same thread and participates in the same
 * JTA transaction. In production the reactive path runs outside any JTA context.
 */
@QuarkusTest
class ReactiveAgentSignatureVerificationServiceIT {

    @Inject
    ReactiveAgentSignatureVerificationService reactiveVerificationService;

    @Inject
    LedgerEntryRepository repo;

    @Inject
    KeyRotationService rotationService;

    @Inject
    AgentSuspectEventCapture eventCapture;

    @Inject
    EntityManager em;

    @InjectMock
    AgentSigner agentSigner;

    @BeforeEach
    void setUp() throws Exception {
        when(agentSigner.sign(anyString(), any())).thenReturn(Optional.empty());
        eventCapture.reset();
    }

    private TestEntry seedEntry(final UUID subjectId, final String actorId) {
        final TestEntry e = new TestEntry();
        e.subjectId = subjectId;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = actorId;
        e.actorType = ActorType.SYSTEM;
        e.actorRole = "Tester";
        return (TestEntry) repo.save(e, DEFAULT_TENANT_ID);
    }

    private TestEntry seedAgent(final UUID subjectId, final String actorId) {
        final TestEntry e = new TestEntry();
        e.subjectId = subjectId;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = actorId;
        e.actorType = ActorType.AGENT;
        e.actorRole = "Reviewer";
        return (TestEntry) repo.save(e, DEFAULT_TENANT_ID);
    }

    private static AgentSignature signEntry(final TestEntry e, final java.security.KeyPair kp) {
        final AgentSignature sig = AgentSignature.signWith(kp, LedgerMerkleTree.canonicalBytes(e));
        e.agentSignature = sig.signature();
        e.agentPublicKey = sig.publicKey();
        e.agentKeyRef = sig.keyRef();
        return sig;
    }

    @Test
    @Transactional
    void verifyAgentSignatureAsync_unsignedEntry_returnsUnsigned() {
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, "unsigned-actor");

        final VerificationResult result = reactiveVerificationService
                .verifyAgentSignatureAsync(e.id, DEFAULT_TENANT_ID)
                .await().atMost(Duration.ofSeconds(5));

        assertThat(result).isEqualTo(VerificationResult.UNSIGNED);
    }

    @Test
    @Transactional
    void verifyAgentSignatureAsync_validSignature_returnsValid() throws Exception {
        final java.security.KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, "signed-actor");
        signEntry(e, kp);
        em.flush();

        final VerificationResult result = reactiveVerificationService
                .verifyAgentSignatureAsync(e.id, DEFAULT_TENANT_ID)
                .await().atMost(Duration.ofSeconds(5));

        assertThat(result).isEqualTo(VerificationResult.VALID);
    }

    @Test
    @Transactional
    void verifyAgentSignatureAsync_tamperedSignature_returnsInvalid() throws Exception {
        final java.security.KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedEntry(sub, "signed-actor");
        signEntry(e, kp);
        e.agentSignature[0] ^= 0xFF;
        em.flush();

        final VerificationResult result = reactiveVerificationService
                .verifyAgentSignatureAsync(e.id, DEFAULT_TENANT_ID)
                .await().atMost(Duration.ofSeconds(5));

        assertThat(result).isEqualTo(VerificationResult.INVALID);
    }

    @Test
    @Transactional
    void verifyAgentSignatureAsync_suspectEntry_firesEventViaReactivePath() throws Exception {
        final java.security.KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedAgent(sub, "claude:reviewer@v1");
        final AgentSignature sig = signEntry(e, kp);
        em.flush();

        final java.time.Instant compromisedSince = e.occurredAt.minusSeconds(60);
        rotationService.recordRotation("claude:reviewer@v1", sig.keyRef(), null,
                KeyRotationReason.COMPROMISED, compromisedSince, DEFAULT_TENANT_ID);

        final VerificationResult result = reactiveVerificationService
                .verifyAgentSignatureAsync(e.id, DEFAULT_TENANT_ID)
                .await().atMost(Duration.ofSeconds(5));

        assertThat(result).isEqualTo(VerificationResult.SUSPECT);
        assertThat(eventCapture.asyncLatch().await(5, TimeUnit.SECONDS)).isTrue();
        final AgentSignatureSuspectEvent event = eventCapture.lastAsyncEvent();
        assertThat(event.entryId()).isEqualTo(e.id);
        assertThat(event.effectiveSince()).isEqualTo(compromisedSince);
    }
}

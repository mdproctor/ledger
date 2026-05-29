package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.AgentSignature;
import io.casehub.ledger.runtime.service.AgentSignatureVerificationService;
import io.casehub.ledger.runtime.service.AgentSigner;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.ledger.runtime.service.model.VerificationResult;
import io.casehub.ledger.service.supplement.TestEntry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AgentSigningIT {

    @Inject
    LedgerEntryRepository repo;

    // signatureService: Ed25519 signing pipeline (verifyAgentSignature)
    // verificationService: Merkle chain integrity (verify) — confirms signing doesn't break the chain
    @Inject
    AgentSignatureVerificationService signatureService;

    @Inject
    LedgerVerificationService verificationService;

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    @InjectMock
    AgentSigner agentSigner;

    private KeyPair testKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        testKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        when(agentSigner.sign(anyString(), any())).thenReturn(Optional.empty());
        when(agentSigner.sign(eq("claude:reviewer@v1"), any()))
                .thenAnswer(inv -> Optional.of(AgentSignature.signWith(testKeyPair, inv.getArgument(1))));
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
    void signedEntry_enrichedByPipeline_verifiesValid() {
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedSigned(sub, 1);

        assertThat(e.agentSignature).as("enricher should have populated agentSignature").isNotNull();
        assertThat(e.agentPublicKey).as("enricher should have populated agentPublicKey").isNotNull();
        assertThat(signatureService.verifyAgentSignature(e.id)).isEqualTo(VerificationResult.VALID);
    }

    @Test
    @Transactional
    void unsignedEntry_noKeyConfigured_returnsUnsigned() {
        final TestEntry e = new TestEntry();
        e.subjectId = UUID.randomUUID();
        e.sequenceNumber = 1;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = "system:noop";
        e.actorType = ActorType.SYSTEM;
        e.actorRole = "System";
        final TestEntry saved = (TestEntry) repo.save(e);

        assertThat(saved.agentSignature).isNull();
        assertThat(signatureService.verifyAgentSignature(saved.id))
                .isEqualTo(VerificationResult.UNSIGNED);
    }

    @Test
    @Transactional
    void tamperedSignatureBytes_returnsInvalid() {
        final UUID sub = UUID.randomUUID();
        final TestEntry e = seedSigned(sub, 1);

        final LedgerEntry stored = repo.findEntryById(e.id).orElseThrow();
        stored.agentSignature[0] ^= 0xFF;
        em.flush();

        assertThat(signatureService.verifyAgentSignature(e.id)).isEqualTo(VerificationResult.INVALID);
    }

    @Test
    @Transactional
    void signedEntry_merkleChainStillValid() {
        final UUID sub = UUID.randomUUID();
        seedSigned(sub, 1);
        seedSigned(sub, 2);

        assertThat(verificationService.verify(sub)).isTrue();
    }
}

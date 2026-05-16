package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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

import io.casehub.ledger.api.model.ActorType;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.AgentKeyProvider;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.ledger.runtime.service.model.VerificationResult;
import io.casehub.ledger.service.supplement.TestEntry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AgentSigningIT {

    @Inject
    LedgerEntryRepository repo;

    @Inject
    LedgerVerificationService verificationService;

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    @InjectMock
    AgentKeyProvider agentKeyProvider;

    private KeyPair testKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        testKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        when(agentKeyProvider.signingKeyPair(anyString())).thenReturn(Optional.empty());
        when(agentKeyProvider.signingKeyPair("claude:reviewer@v1"))
                .thenReturn(Optional.of(testKeyPair));
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
        assertThat(verificationService.verifyAgentSignature(e.id)).isEqualTo(VerificationResult.VALID);
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
        assertThat(verificationService.verifyAgentSignature(saved.id))
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

        assertThat(verificationService.verifyAgentSignature(e.id)).isEqualTo(VerificationResult.INVALID);
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

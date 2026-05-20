package io.casehub.ledger.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.ActorType;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.service.model.VerificationResult;
import io.casehub.ledger.service.supplement.TestEntry;

class AgentCryptographicVerifierTest {

    private KeyPair keyPair;
    private TestEntry entry;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        entry = new TestEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = UUID.randomUUID();
        entry.sequenceNumber = 1;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "claude:reviewer@v1";
        entry.actorType = ActorType.AGENT;
        entry.actorRole = "Reviewer";
        entry.occurredAt = Instant.now();
    }

    @Test
    void validSignature_returnsValid() throws Exception {
        final byte[] canonical = LedgerMerkleTree.canonicalBytes(entry);
        final java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
        sig.initSign(keyPair.getPrivate());
        sig.update(canonical);
        entry.agentSignature = sig.sign();
        entry.agentPublicKey = keyPair.getPublic().getEncoded();

        assertThat(AgentCryptographicVerifier.verifyCryptographic(entry))
                .isEqualTo(VerificationResult.VALID);
    }

    @Test
    void tamperedSignature_returnsInvalid() throws Exception {
        final byte[] canonical = LedgerMerkleTree.canonicalBytes(entry);
        final java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
        sig.initSign(keyPair.getPrivate());
        sig.update(canonical);
        final byte[] signature = sig.sign();
        signature[0] ^= 0xFF;
        entry.agentSignature = signature;
        entry.agentPublicKey = keyPair.getPublic().getEncoded();

        assertThat(AgentCryptographicVerifier.verifyCryptographic(entry))
                .isEqualTo(VerificationResult.INVALID);
    }

    @Test
    void missingPublicKey_returnsInvalid() throws Exception {
        entry.agentSignature = new byte[]{0x01};
        entry.agentPublicKey = null;

        assertThat(AgentCryptographicVerifier.verifyCryptographic(entry))
                .isEqualTo(VerificationResult.INVALID);
    }

    @Test
    void mutatedCanonicalField_returnsInvalid() throws Exception {
        final byte[] canonical = LedgerMerkleTree.canonicalBytes(entry);
        final java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
        sig.initSign(keyPair.getPrivate());
        sig.update(canonical);
        entry.agentSignature = sig.sign();
        entry.agentPublicKey = keyPair.getPublic().getEncoded();

        // Mutate actorId after signing — canonical bytes will differ
        entry.actorId = "impersonator@v1";

        assertThat(AgentCryptographicVerifier.verifyCryptographic(entry))
                .isEqualTo(VerificationResult.INVALID);
    }
}

package io.casehub.ledger.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.core.model.VerificationResult;
import io.casehub.ledger.core.signing.AgentCryptographicVerifier;
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
        final byte[] canonical = entry.canonicalBytes();
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
        final byte[] canonical = entry.canonicalBytes();
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
    void unrecognisedPublicKeyBytes_returnsInvalid() {
        entry.agentSignature = new byte[]{0x01};
        entry.agentPublicKey = new byte[]{0x00, 0x01, 0x02, 0x03}; // not a valid X.509 key

        assertThat(AgentCryptographicVerifier.verifyCryptographic(entry))
                .isEqualTo(VerificationResult.INVALID);
    }

    @Test
    void mutatedCanonicalField_returnsInvalid() throws Exception {
        final byte[] canonical = entry.canonicalBytes();
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

    @Test
    void validSignature_ecP256_returnsValid() throws Exception {
        // Generate EC P-256 keypair
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        KeyPair ecKeyPair = gen.generateKeyPair();

        final byte[] canonical = entry.canonicalBytes();
        final java.security.Signature sig = java.security.Signature.getInstance("SHA256withECDSA");
        sig.initSign(ecKeyPair.getPrivate());
        sig.update(canonical);
        entry.agentSignature = sig.sign();
        entry.agentPublicKey = ecKeyPair.getPublic().getEncoded();

        assertThat(AgentCryptographicVerifier.verifyCryptographic(entry))
                .isEqualTo(VerificationResult.VALID);
    }

    @Test
    void tamperedSignature_ecP256_returnsInvalid() throws Exception {
        // Generate EC P-256 keypair
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        KeyPair ecKeyPair = gen.generateKeyPair();

        final byte[] canonical = entry.canonicalBytes();
        final java.security.Signature sig = java.security.Signature.getInstance("SHA256withECDSA");
        sig.initSign(ecKeyPair.getPrivate());
        sig.update(canonical);
        final byte[] signature = sig.sign();
        signature[5] ^= 0xFF;  // Tamper with signature
        entry.agentSignature = signature;
        entry.agentPublicKey = ecKeyPair.getPublic().getEncoded();

        assertThat(AgentCryptographicVerifier.verifyCryptographic(entry))
                .isEqualTo(VerificationResult.INVALID);
    }
}

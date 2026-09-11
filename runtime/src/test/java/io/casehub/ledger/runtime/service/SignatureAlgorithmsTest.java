package io.casehub.ledger.runtime.service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import io.casehub.ledger.core.signing.SignatureAlgorithms;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignatureAlgorithmsTest {

    @Test
    void signatureAlgorithm_p256_returnsSha256withEcdsa() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = gen.generateKeyPair();

        String algo = SignatureAlgorithms.signatureAlgorithm(keyPair.getPublic());

        assertThat(algo).isEqualTo("SHA256withECDSA");
    }

    @Test
    void signatureAlgorithm_p384_returnsSha384withEcdsa() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp384r1"));
        KeyPair keyPair = gen.generateKeyPair();

        String algo = SignatureAlgorithms.signatureAlgorithm(keyPair.getPublic());

        assertThat(algo).isEqualTo("SHA384withECDSA");
    }

    @Test
    void signatureAlgorithm_p521_returnsSha512withEcdsa() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp521r1"));
        KeyPair keyPair = gen.generateKeyPair();

        String algo = SignatureAlgorithms.signatureAlgorithm(keyPair.getPublic());

        assertThat(algo).isEqualTo("SHA512withECDSA");
    }

    @Test
    void signatureAlgorithm_ed25519_passesThrough() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = gen.generateKeyPair();

        String algo = SignatureAlgorithms.signatureAlgorithm(keyPair.getPublic());

        // Java 26 returns "EdDSA" for Ed25519 keys
        assertThat(algo).isEqualTo("EdDSA");
    }
}

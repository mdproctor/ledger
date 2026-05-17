package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.runtime.service.SigningKey;

class SigningKeyTest {

    @Test
    void keyRef_isDerivedFromPublicKey() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final SigningKey sk = SigningKey.of(kp);

        assertThat(sk.keyRef()).isNotNull().isNotEmpty();
        assertThat(sk.keyPair()).isSameAs(kp);
    }

    @Test
    void keyRef_isSameForSamePublicKey() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();

        final SigningKey sk1 = SigningKey.of(kp);
        final SigningKey sk2 = SigningKey.of(kp);

        assertThat(sk1.keyRef()).isEqualTo(sk2.keyRef());
    }

    @Test
    void keyRef_differsBetweenDistinctKeys() throws Exception {
        final KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
        final KeyPair kp1 = gen.generateKeyPair();
        final KeyPair kp2 = gen.generateKeyPair();

        assertThat(SigningKey.of(kp1).keyRef())
                .isNotEqualTo(SigningKey.of(kp2).keyRef());
    }

    @Test
    void keyRef_isBase64UrlEncoded() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final String keyRef = SigningKey.of(kp).keyRef();

        // Base64URL without padding: only A-Z, a-z, 0-9, -, _
        assertThat(keyRef).matches("[A-Za-z0-9_-]+");
        // SHA-256 → 32 bytes → 43 Base64URL chars (no padding)
        assertThat(keyRef).hasSize(43);
    }

    @Test
    void keyRef_derivableFromPublicKeyBytes() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final SigningKey sk = SigningKey.of(kp);

        final byte[] pubKeyBytes = kp.getPublic().getEncoded();
        final byte[] hash = MessageDigest.getInstance("SHA-256").digest(pubKeyBytes);
        final String computed = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

        assertThat(sk.keyRef()).isEqualTo(computed);
    }
}

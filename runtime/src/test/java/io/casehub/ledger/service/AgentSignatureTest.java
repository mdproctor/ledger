package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.runtime.service.AgentSignature;

class AgentSignatureTest {

    @Test
    void signWith_producesVerifiableSignature() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final byte[] data = "canonical bytes".getBytes();

        final AgentSignature sig = AgentSignature.signWith(kp, data);

        final Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(kp.getPublic());
        verifier.update(data);
        assertThat(verifier.verify(sig.signature())).isTrue();
    }

    @Test
    void signWith_publicKeyIsX509Encoded() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final AgentSignature sig = AgentSignature.signWith(kp, new byte[]{1, 2, 3});
        assertThat(sig.publicKey()).isEqualTo(kp.getPublic().getEncoded());
    }

    @Test
    void signWith_keyRefIsSha256OfPublicKeyBase64Url() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final AgentSignature sig = AgentSignature.signWith(kp, new byte[]{1, 2, 3});

        final byte[] hash = MessageDigest.getInstance("SHA-256").digest(kp.getPublic().getEncoded());
        final String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        assertThat(sig.keyRef()).isEqualTo(expected);
    }

    @Test
    void keyRef_isBase64UrlNoPadding_43chars() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final AgentSignature sig = AgentSignature.signWith(kp, new byte[]{1});
        assertThat(sig.keyRef()).matches("[A-Za-z0-9_-]+").hasSize(43);
    }

    @Test
    void compactConstructor_defensiveCopy_signature() {
        final byte[] sig = {1, 2, 3};
        final byte[] pub = {4, 5, 6};
        final AgentSignature as = new AgentSignature(sig, pub, "keyref");
        sig[0] = 99;
        assertThat(as.signature()[0]).isEqualTo((byte) 1);
    }

    @Test
    void compactConstructor_defensiveCopy_publicKey() {
        final byte[] sig = {1, 2, 3};
        final byte[] pub = {4, 5, 6};
        final AgentSignature as = new AgentSignature(sig, pub, "keyref");
        pub[0] = 99;
        assertThat(as.publicKey()[0]).isEqualTo((byte) 4);
    }
}

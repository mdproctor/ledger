package io.casehub.ledger.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.runtime.service.AgentSignature;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class InMemoryAgentSignerTest {

    @Inject
    InMemoryAgentSigner signer;

    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        signer.clear();
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    @Test
    void unregisteredActor_returnsEmpty() {
        assertThat(signer.sign("unknown-actor", new byte[]{1, 2, 3})).isEmpty();
    }

    @Test
    void registeredActor_returnsSignature() {
        signer.register("claude:reviewer@v1", keyPair);

        final var result = signer.sign("claude:reviewer@v1", new byte[]{1, 2, 3});

        assertThat(result).isPresent();
        final AgentSignature sig = result.get();
        assertThat(sig.signature()).isNotEmpty();
        assertThat(sig.publicKey()).isEqualTo(keyPair.getPublic().getEncoded());
        assertThat(sig.keyRef()).isNotBlank();
    }

    @Test
    void differentActors_useTheirOwnKeys() throws NoSuchAlgorithmException {
        final KeyPair kp2 = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        signer.register("actor-a", keyPair);
        signer.register("actor-b", kp2);

        final var sigA = signer.sign("actor-a", new byte[]{1}).orElseThrow();
        final var sigB = signer.sign("actor-b", new byte[]{1}).orElseThrow();

        assertThat(sigA.publicKey()).isEqualTo(keyPair.getPublic().getEncoded());
        assertThat(sigB.publicKey()).isEqualTo(kp2.getPublic().getEncoded());
        assertThat(sigA.keyRef()).isNotEqualTo(sigB.keyRef());
    }

    @Test
    void signature_isVerifiableByJca() throws Exception {
        signer.register("claude:reviewer@v1", keyPair);
        final byte[] data = new byte[]{4, 5, 6, 7};

        final AgentSignature sig = signer.sign("claude:reviewer@v1", data).orElseThrow();

        final Signature verifier = Signature.getInstance(keyPair.getPublic().getAlgorithm());
        verifier.initVerify(keyPair.getPublic());
        verifier.update(data);
        assertThat(verifier.verify(sig.signature())).isTrue();
    }

    @Test
    void clear_removesAllRegistrations() {
        signer.register("actor-a", keyPair);
        signer.clear();

        assertThat(signer.sign("actor-a", new byte[]{1})).isEmpty();
    }
}

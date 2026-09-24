package io.casehub.ledger.signing.gcp.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.core.signing.AgentKeyMaterial;
import io.casehub.ledger.core.signing.AgentSignature;
import io.casehub.ledger.core.signing.AgentSigner;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Quarkus integration test for {@link GcpKmsAgentSigner}.
 *
 * <p>Uses a mocked {@link io.casehub.ledger.signing.gcp.GcpKmsClientWrapper} via test CDI producer.
 * Tests signing via AgentSigner SPI, verification round-trip, keyMaterial() without calling sign,
 * and unconfigured actor returning empty.
 */
@QuarkusTest
class GcpKmsAgentSignerIT {

    @Inject
    AgentSigner agentSigner;

    @Test
    void signsData_viaGcpKms() throws Exception {
        final byte[] data = "canonical ledger bytes".getBytes();

        final Optional<AgentSignature> result = agentSigner.sign("configured-actor", data);

        assertThat(result).isPresent();
        assertThat(result.get().publicKey()).isNotNull();
        assertThat(result.get().signature()).isNotNull();
        assertThat(result.get().keyRef()).isNotBlank();

        // Round-trip: verify the signature with JCA (same verification path as AgentCryptographicVerifier)
        // The mock uses a real P-256 key pair, so verification should succeed
        final KeyFactory kf = KeyFactory.getInstance("EC");
        final PublicKey pub = kf.generatePublic(
                new X509EncodedKeySpec(result.get().publicKey()));
        final Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(pub);
        verifier.update(data);
        assertThat(verifier.verify(result.get().signature())).isTrue();
    }

    @Test
    void keyMaterial_returnsKeyWithoutSign() {
        final Optional<AgentKeyMaterial> result = agentSigner.keyMaterial("configured-actor");

        assertThat(result).isPresent();
        assertThat(result.get().publicKey()).isNotNull();
        assertThat(result.get().keyRef()).isNotBlank();
    }

    @Test
    void unconfiguredActor_returnsEmpty() {
        final Optional<AgentSignature> result = agentSigner.sign("unconfigured-actor", new byte[]{1, 2, 3});

        assertThat(result).isEmpty();
    }
}

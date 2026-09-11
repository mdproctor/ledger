package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.core.signing.AgentKeyMaterial;
import io.casehub.ledger.core.signing.AgentSignature;
import io.casehub.ledger.core.signing.AgentSigner;

/**
 * SPI contract test for {@link AgentSigner#keyMaterial(String)} default method.
 * Per protocol PP-20260513-2ce9e1.
 */
class AgentSignerContractTest {

    @Test
    void keyMaterial_returnsPresent_whenSignReturnsPresent() {
        final byte[] dummyPubKey = new byte[]{1, 2, 3};
        final AgentSigner signer = (actorId, data) ->
                Optional.of(new AgentSignature(data, dummyPubKey, "test-ref"));

        final Optional<AgentKeyMaterial> result = signer.keyMaterial("actor1");

        assertThat(result).isPresent();
        assertThat(result.get().keyRef()).isEqualTo("test-ref");
        assertThat(result.get().publicKey()).isEqualTo(dummyPubKey);
    }

    @Test
    void keyMaterial_returnsEmpty_whenSignReturnsEmpty() {
        final AgentSigner signer = (actorId, data) -> Optional.empty();

        final Optional<AgentKeyMaterial> result = signer.keyMaterial("actor1");

        assertThat(result).isEmpty();
    }

    @Test
    void keyMaterial_passesActorId_toSign() {
        final String[] capturedActorId = new String[1];
        final AgentSigner signer = (actorId, data) -> {
            capturedActorId[0] = actorId;
            return Optional.of(new AgentSignature(data, new byte[]{1}, "ref"));
        };

        signer.keyMaterial("actor-xyz");

        assertThat(capturedActorId[0]).isEqualTo("actor-xyz");
    }
}

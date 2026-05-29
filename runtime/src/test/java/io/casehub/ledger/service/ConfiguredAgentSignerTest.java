package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.service.AgentSignature;
import io.casehub.ledger.runtime.service.ConfiguredAgentSigner;

class ConfiguredAgentSignerTest {

    @TempDir Path tempDir;

    private Path writeKeyPem(final String filename, final String type, final byte[] encoded)
            throws Exception {
        final String pem = "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                + "\n-----END " + type + "-----\n";
        final Path file = tempDir.resolve(filename);
        Files.writeString(file, pem);
        return file;
    }

    private LedgerConfig mockConfig(final Map<String, LedgerConfig.AgentSigningConfig.ActorKeyConfig> keys) {
        final LedgerConfig.AgentSigningConfig signingConfig = mock(LedgerConfig.AgentSigningConfig.class);
        when(signingConfig.keys()).thenReturn(keys);
        final LedgerConfig config = mock(LedgerConfig.class);
        when(config.agentSigning()).thenReturn(signingConfig);
        return config;
    }

    @Test
    void sign_returnsValidSignature_forConfiguredActor() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final Path privPath = writeKeyPem("priv.pem", "PRIVATE KEY", kp.getPrivate().getEncoded());
        final Path pubPath = writeKeyPem("pub.pem", "PUBLIC KEY", kp.getPublic().getEncoded());

        final LedgerConfig.AgentSigningConfig.ActorKeyConfig actorConfig =
                mock(LedgerConfig.AgentSigningConfig.ActorKeyConfig.class);
        when(actorConfig.privateKey()).thenReturn(privPath.toString());
        when(actorConfig.publicKey()).thenReturn(pubPath.toString());

        final ConfiguredAgentSigner signer =
                new ConfiguredAgentSigner(mockConfig(Map.of("claude:reviewer@v1", actorConfig)));
        signer.loadKeys();

        final byte[] data = "test canonical bytes".getBytes();
        final Optional<AgentSignature> result = signer.sign("claude:reviewer@v1", data);

        assertThat(result).isPresent();
        final Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(kp.getPublic());
        verifier.update(data);
        assertThat(verifier.verify(result.get().signature())).isTrue();
        assertThat(result.get().publicKey()).isEqualTo(kp.getPublic().getEncoded());
    }

    @Test
    void sign_returnsEmpty_forUnconfiguredActor() {
        final ConfiguredAgentSigner signer = new ConfiguredAgentSigner(mockConfig(Map.of()));
        signer.loadKeys();
        assertThat(signer.sign("unknown-actor", new byte[]{1})).isEmpty();
    }

    @Test
    void sign_returnsEmpty_forFailedKeyLoad_neverThrows() throws Exception {
        final LedgerConfig.AgentSigningConfig.ActorKeyConfig actorConfig =
                mock(LedgerConfig.AgentSigningConfig.ActorKeyConfig.class);
        when(actorConfig.privateKey()).thenReturn("/does/not/exist.pem");
        when(actorConfig.publicKey()).thenReturn("/does/not/exist.pem");

        final ConfiguredAgentSigner signer =
                new ConfiguredAgentSigner(mockConfig(Map.of("bad-actor", actorConfig)));
        signer.loadKeys();  // logs error, adds to failedActors

        assertThat(signer.sign("bad-actor", new byte[]{1})).isEmpty();
        assertThat(signer.sign("bad-actor", new byte[]{2})).isEmpty();
    }
}

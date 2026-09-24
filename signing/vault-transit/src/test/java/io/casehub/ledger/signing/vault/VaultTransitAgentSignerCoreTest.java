package io.casehub.ledger.signing.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.core.signing.AgentSignature;

class VaultTransitAgentSignerCoreTest {

    private static final String ACTOR = "agent:reviewer@v1";
    private static final String KEY_NAME = "reviewer-key";
    private static final byte[] DATA = "test-data".getBytes();

    @Test
    void signsDataWhenKeyMappingExists() throws Exception {
        var keyPair = generateEd25519KeyPair();
        var client = new StubVaultTransitClient(keyPair.getPublic(), new byte[]{1, 2, 3});
        var tokenSource = new StubTokenSource("test-token");
        var config = new VaultTransitSigningConfig("http://localhost:8200", Map.of(ACTOR, KEY_NAME));

        var signer = new VaultTransitAgentSignerCore(config, client, tokenSource);

        var result = signer.sign(ACTOR, DATA);

        assertThat(result).isPresent();
        assertThat(result.get().signature()).containsExactly(1, 2, 3);
        assertThat(result.get().publicKey()).isEqualTo(keyPair.getPublic().getEncoded());
    }

    @Test
    void returnsEmptyWhenActorNotMapped() {
        var client = new StubVaultTransitClient(null, null);
        var tokenSource = new StubTokenSource("test-token");
        var config = new VaultTransitSigningConfig("http://localhost:8200", Map.of());

        var signer = new VaultTransitAgentSignerCore(config, client, tokenSource);

        assertThat(signer.sign("unknown-actor", DATA)).isEmpty();
    }

    @Test
    void retriesOnAuthenticationFailure() throws Exception {
        var keyPair = generateEd25519KeyPair();
        var fetchCallCount = new AtomicInteger(0);
        var client = new VaultTransitSigningClient(
                new VaultTransitSigningConfig("http://localhost:8200", Map.of(ACTOR, KEY_NAME)),
                java.net.http.HttpClient.newBuilder().build(),
                new com.fasterxml.jackson.databind.ObjectMapper()) {
            @Override
            public PublicKey fetchPublicKey(String token, String keyName) {
                if (fetchCallCount.incrementAndGet() == 1) {
                    throw new VaultAuthenticationException("403");
                }
                return keyPair.getPublic();
            }

            @Override
            public byte[] sign(String token, String keyName, byte[] data) {
                return new byte[]{4, 5, 6};
            }
        };

        var invalidated = new AtomicBoolean(false);
        var tokenSource = new VaultTokenSource() {
            @Override
            public String token() { return "token-" + fetchCallCount.get(); }
            @Override
            public void invalidate() { invalidated.set(true); }
        };

        var config = new VaultTransitSigningConfig("http://localhost:8200", Map.of(ACTOR, KEY_NAME));
        var signer = new VaultTransitAgentSignerCore(config, client, tokenSource);

        var result = signer.sign(ACTOR, DATA);

        assertThat(result).isPresent();
        assertThat(invalidated).isTrue();
        assertThat(fetchCallCount.get()).isEqualTo(2);
    }

    @Test
    void createTokenSourceRejectsJwtAndJwtPathTogether() {
        var authConfig = new VaultTransitAuthConfig(
                VaultTransitAuthConfig.AuthMethod.JWT,
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of("my-role"),
                Optional.of("/path/to/jwt"),
                Optional.of("raw-jwt-string"),
                Optional.empty());

        assertThatThrownBy(() -> VaultTransitAgentSignerCore.createTokenSource(
                authConfig, "http://localhost:8200", null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("specify jwt-path or jwt, not both");
    }

    @Test
    void createTokenSourceReturnsStaticTokenSource() {
        var authConfig = new VaultTransitAuthConfig(
                VaultTransitAuthConfig.AuthMethod.TOKEN,
                Optional.of("my-token"),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());

        var source = VaultTransitAgentSignerCore.createTokenSource(
                authConfig, "http://localhost:8200", null, null, null);

        assertThat(source).isInstanceOf(StaticVaultTokenSource.class);
        assertThat(source.token()).isEqualTo("my-token");
    }

    @Test
    void cachesContextBetweenCalls() throws Exception {
        var keyPair = generateEd25519KeyPair();
        var fetchCount = new AtomicInteger(0);
        var client = new StubVaultTransitClient(keyPair.getPublic(), new byte[]{1, 2, 3}) {
            @Override
            public PublicKey fetchPublicKey(String token, String keyName) {
                fetchCount.incrementAndGet();
                return super.fetchPublicKey(token, keyName);
            }
        };
        var tokenSource = new StubTokenSource("test-token");
        var config = new VaultTransitSigningConfig("http://localhost:8200", Map.of(ACTOR, KEY_NAME));

        var signer = new VaultTransitAgentSignerCore(config, client, tokenSource);

        signer.sign(ACTOR, DATA);
        signer.sign(ACTOR, DATA);

        assertThat(fetchCount.get()).isEqualTo(1);
    }

    @Test
    void invalidateAllClearsCache() throws Exception {
        var keyPair = generateEd25519KeyPair();
        var fetchCount = new AtomicInteger(0);
        var client = new StubVaultTransitClient(keyPair.getPublic(), new byte[]{1, 2, 3}) {
            @Override
            public PublicKey fetchPublicKey(String token, String keyName) {
                fetchCount.incrementAndGet();
                return super.fetchPublicKey(token, keyName);
            }
        };
        var tokenSource = new StubTokenSource("test-token");
        var config = new VaultTransitSigningConfig("http://localhost:8200", Map.of(ACTOR, KEY_NAME));

        var signer = new VaultTransitAgentSignerCore(config, client, tokenSource);

        signer.sign(ACTOR, DATA);
        signer.invalidateAll();
        signer.sign(ACTOR, DATA);

        assertThat(fetchCount.get()).isEqualTo(2);
    }

    private static KeyPair generateEd25519KeyPair() throws Exception {
        var gen = KeyPairGenerator.getInstance("Ed25519");
        return gen.generateKeyPair();
    }

    private static class StubVaultTransitClient extends VaultTransitSigningClient {
        private final PublicKey publicKey;
        private final byte[] signatureBytes;

        StubVaultTransitClient(PublicKey publicKey, byte[] signatureBytes) {
            super(new VaultTransitSigningConfig("http://localhost:8200", Map.of()),
                    java.net.http.HttpClient.newBuilder().build(),
                    new com.fasterxml.jackson.databind.ObjectMapper());
            this.publicKey = publicKey;
            this.signatureBytes = signatureBytes;
        }

        @Override
        public PublicKey fetchPublicKey(String token, String keyName) {
            return publicKey;
        }

        @Override
        public byte[] sign(String token, String keyName, byte[] data) {
            return signatureBytes;
        }
    }

    private record StubTokenSource(String tokenValue) implements VaultTokenSource {
        @Override
        public String token() { return tokenValue; }
        @Override
        public void invalidate() {}
    }
}

package io.casehub.ledger.examples.vault;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.casehub.ledger.runtime.service.AgentSignature;
import io.casehub.ledger.runtime.service.AgentSigner;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class VaultTransitAgentSignerIT {

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(8099);
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
        ((VaultTransitAgentSigner) agentSigner).invalidateAll();
    }

    @Inject
    AgentSigner agentSigner;

    /** Returns the public key PEM as a Java string with real newlines. */
    private static String publicKeyPem(final KeyPair kp) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'})
                        .encodeToString(kp.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
    }

    /**
     * Builds a Vault Transit key-info JSON response body.
     * Newlines in the PEM are escaped as \n (JSON escape) so the JSON is valid.
     * Jackson's asText() will un-escape them back to real newlines when parsed.
     */
    private static String keyInfoResponse(final KeyPair kp) {
        final String pemJsonSafe = publicKeyPem(kp)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
        return "{\"data\":{\"keys\":{\"1\":{\"public_key\":\"" + pemJsonSafe + "\"}}}}";
    }

    private static String signResponse(final byte[] sigBytes) {
        return "{\"data\":{\"signature\":\"vault:v1:" +
                Base64.getEncoder().encodeToString(sigBytes) + "\"}}";
    }

    private static void stubKeyInfo(final KeyPair kp) {
        wireMock.stubFor(get(urlEqualTo("/v1/transit/keys/reviewer-key"))
                .withHeader("X-Vault-Token", equalTo("test-token"))
                .willReturn(okJson(keyInfoResponse(kp))));
    }

    private static byte[] realSign(final KeyPair kp, final byte[] data) throws Exception {
        final Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(kp.getPrivate());
        sig.update(data);
        return sig.sign();
    }

    private static void stubSign(final KeyPair kp, final byte[] data) throws Exception {
        final byte[] sigBytes = realSign(kp, data);
        wireMock.stubFor(post(urlEqualTo("/v1/transit/sign/reviewer-key"))
                .withHeader("X-Vault-Token", equalTo("test-token"))
                .willReturn(okJson(signResponse(sigBytes))));
    }

    @Test
    void signsData_viaVaultTransitApi() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final byte[] data = "canonical ledger bytes".getBytes();
        stubKeyInfo(kp);
        stubSign(kp, data);

        final Optional<AgentSignature> result = agentSigner.sign("claude:reviewer@v1", data);

        assertThat(result).isPresent();
        assertThat(result.get().publicKey()).isEqualTo(kp.getPublic().getEncoded());
        final Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(kp.getPublic());
        verifier.update(data);
        assertThat(verifier.verify(result.get().signature())).isTrue();
    }

    @Test
    void cachesPublicKey_secondSignCallDoesNotRefetchKeyInfo() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final byte[] data1 = "data one".getBytes();
        stubKeyInfo(kp);
        wireMock.stubFor(post(urlEqualTo("/v1/transit/sign/reviewer-key"))
                .willReturn(okJson(signResponse(realSign(kp, data1)))));

        agentSigner.sign("claude:reviewer@v1", data1);
        agentSigner.sign("claude:reviewer@v1", "data two".getBytes());

        wireMock.verify(1, getRequestedFor(urlEqualTo("/v1/transit/keys/reviewer-key")));
    }

    @Test
    void returnsEmpty_forUnmappedActor() {
        final Optional<AgentSignature> result = agentSigner.sign("unmapped-actor", new byte[]{1});
        assertThat(result).isEmpty();
        wireMock.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void throwsOnVault403_notCached_retrySucceeds() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        wireMock.stubFor(get(urlEqualTo("/v1/transit/keys/reviewer-key"))
                .willReturn(forbidden()));

        assertThatThrownBy(() -> agentSigner.sign("claude:reviewer@v1", new byte[]{1}))
                .isInstanceOf(RuntimeException.class);

        wireMock.resetAll();
        final byte[] data = "retry data".getBytes();
        stubKeyInfo(kp);
        stubSign(kp, data);

        final Optional<AgentSignature> result = agentSigner.sign("claude:reviewer@v1", data);
        assertThat(result).isPresent();
    }
}

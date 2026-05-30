package io.casehub.ledger.service.identity;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.service.identity.WebDIDResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebDIDResolverTest {

    static WireMockServer wm;

    /** Resolver with SSRF check bypassed — connects to localhost WireMock. */
    WebDIDResolver resolver;

    @BeforeAll
    static void startWireMock() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
    }

    @AfterAll
    static void stopWireMock() {
        wm.stop();
    }

    @BeforeEach
    void setUp() {
        wm.resetAll();
        resolver = new WebDIDResolver(stubConfig()) {
            @Override
            protected boolean isAllowedHost(final String host) {
                return true; // allow localhost in tests
            }

            @Override
            protected String scheme() {
                return "http"; // WireMock serves plain HTTP, not HTTPS
            }
        };
    }

    // -------------------------------------------------------------------------
    // SSRF / routing checks
    // -------------------------------------------------------------------------

    @Test
    void returnsEmptyForNonWebMethod() {
        assertThat(resolver.resolve("did:key:z6Mk")).isEmpty();
        assertThat(resolver.resolve("did:ethr:0xabc")).isEmpty();
        assertThat(resolver.resolve(null)).isEmpty();
    }

    @Test
    void rejectsBlockedHostsForSsrf() {
        // Use real resolver (no SSRF override) to confirm SSRF rejection
        final WebDIDResolver real = new WebDIDResolver(stubConfig());
        assertThat(real.resolve("did:web:localhost")).isEmpty();
        assertThat(real.resolve("did:web:127.0.0.1")).isEmpty();
        assertThat(real.resolve("did:web:192.168.1.1")).isEmpty();
        assertThat(real.resolve("did:web:10.0.0.1")).isEmpty();
        assertThat(real.resolve("did:web:172.16.0.1")).isEmpty();
        assertThat(real.resolve("did:web:0.0.0.0")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // HTTP response handling
    // -------------------------------------------------------------------------

    @Test
    void returns404AsEmpty() {
        wm.stubFor(get(anyUrl()).willReturn(notFound()));
        // Port is percent-encoded per did:web spec: %3A separates host from port
        final String did = "did:web:localhost%3A" + wm.port();
        assertThat(resolver.resolve(did)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // DID document parsing
    // -------------------------------------------------------------------------

    @Test
    void parsesDocumentWithAlsoKnownAs() {
        final String doc = """
                {
                  "id": "did:web:example.com",
                  "verificationMethod": [],
                  "alsoKnownAs": ["claude:reviewer@v1"]
                }
                """;
        wm.stubFor(get("/.well-known/did.json").willReturn(okJson(doc)));
        // Port is percent-encoded per did:web spec
        final String did = "did:web:localhost%3A" + wm.port();
        final var result = resolver.resolve(did);
        assertThat(result).isPresent();
        assertThat(result.get().alsoKnownAs()).containsExactly("claude:reviewer@v1");
        assertThat(result.get().id()).isEqualTo("did:web:example.com");
    }

    @Test
    void parsesVerificationMethodWithPublicKeyMultibase() throws Exception {
        final var gen = KeyPairGenerator.getInstance("Ed25519");
        final var kp = gen.generateKeyPair();
        final byte[] encoded = kp.getPublic().getEncoded();
        // Our convention: 'z' prefix + base64url-encoded key bytes
        final String multibase = "z" + Base64.getUrlEncoder().withoutPadding().encodeToString(encoded);

        final String doc = """
                {
                  "id": "did:web:example.com",
                  "verificationMethod": [
                    {
                      "id": "did:web:example.com#key-1",
                      "type": "Ed25519VerificationKey2020",
                      "publicKeyMultibase": "%s"
                    }
                  ]
                }
                """.formatted(multibase);
        wm.stubFor(get("/.well-known/did.json").willReturn(okJson(doc)));
        // Port is percent-encoded per did:web spec
        final String did = "did:web:localhost%3A" + wm.port();
        final var result = resolver.resolve(did);
        assertThat(result).isPresent();
        assertThat(result.get().verificationMethods()).hasSize(1);
        final var vm = result.get().verificationMethods().get(0);
        assertThat(vm.publicKeyBytes()).isEqualTo(encoded);
        assertThat(vm.type()).isEqualTo("Ed25519VerificationKey2020");
        assertThat(vm.id()).isEqualTo("did:web:example.com#key-1");
    }

    @Test
    void toleratesInvalidMultibaseGracefully() {
        final String doc = """
                {
                  "id": "did:web:example.com",
                  "verificationMethod": [
                    {
                      "id": "did:web:example.com#key-1",
                      "type": "Ed25519VerificationKey2020",
                      "publicKeyMultibase": "z!@#INVALID"
                    }
                  ]
                }
                """;
        wm.stubFor(get("/.well-known/did.json").willReturn(okJson(doc)));
        // Port is percent-encoded per did:web spec
        final var result = resolver.resolve("did:web:localhost%3A" + wm.port());
        // Should still return a document, just with empty key bytes
        assertThat(result).isPresent();
        assertThat(result.get().verificationMethods().get(0).publicKeyBytes()).isEmpty();
    }

    @Test
    void resolvesPathDid() {
        // did:web:example.com:users:alice → /users/alice/did.json
        final String doc = """
                {
                  "id": "did:web:example.com:users:alice",
                  "verificationMethod": [],
                  "alsoKnownAs": ["alice@example.com"]
                }
                """;
        wm.stubFor(get("/users/alice/did.json").willReturn(okJson(doc)));
        // Port is percent-encoded; colons after the authority are path separators
        final String did = "did:web:localhost%3A" + wm.port() + ":users:alice";
        final var result = resolver.resolve(did);
        assertThat(result).isPresent();
        assertThat(result.get().alsoKnownAs()).containsExactly("alice@example.com");
    }

    @Test
    void returnsEmptyOnMalformedJson() {
        wm.stubFor(get(anyUrl()).willReturn(okJson("NOT_JSON_AT_ALL!!!")));
        assertThat(resolver.resolve("did:web:localhost%3A" + wm.port())).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private LedgerConfig stubConfig() {
        final LedgerConfig.AgentIdentityConfig agentIdentityConfig = mock(LedgerConfig.AgentIdentityConfig.class);
        when(agentIdentityConfig.webResolverTimeoutMs()).thenReturn(5000);
        when(agentIdentityConfig.webResolverMaxResponseBytes()).thenReturn(1_048_576);
        final LedgerConfig config = mock(LedgerConfig.class);
        when(config.agentIdentity()).thenReturn(agentIdentityConfig);
        return config;
    }
}

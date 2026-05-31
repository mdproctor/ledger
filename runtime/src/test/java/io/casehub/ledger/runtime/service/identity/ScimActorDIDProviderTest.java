package io.casehub.ledger.runtime.service.identity;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.casehub.ledger.runtime.service.AgentKeyRotatedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class ScimActorDIDProviderTest {

    private WireMockServer wm;
    private ScimActorDIDProvider provider;

    private static String scimResponse(final String did) {
        return """
            {
              "totalResults": 1,
              "Resources": [{
                "urn:ietf:params:scim:schemas:extension:casehub:2.0:Agent": {
                  "did": "%s"
                }
              }]
            }
            """.formatted(did);
    }

    private static final String EMPTY_RESPONSE = """
        {"totalResults": 0, "Resources": []}
        """;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(wireMockConfig().dynamicPort());
        wm.start();
        provider = new ScimActorDIDProvider(
                "http://localhost:" + wm.port(), "test-token", 5000, Duration.ofMinutes(5));
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    @Test
    void didFor_returnsDid_andCachesResult() {
        wm.stubFor(get(urlPathEqualTo("/scim/v2/Agents"))
                .withQueryParam("filter", equalTo("externalId eq \"claude:reviewer@v1\""))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(scimResponse("did:web:example.com:agents:reviewer"))));

        assertThat(provider.didFor("claude:reviewer@v1"))
                .contains("did:web:example.com:agents:reviewer");
        provider.didFor("claude:reviewer@v1");
        wm.verify(1, getRequestedFor(urlPathEqualTo("/scim/v2/Agents")));
    }

    @Test
    void didFor_encodesActorIdColonAndAt() {
        wm.stubFor(get(urlPathEqualTo("/scim/v2/Agents"))
                .withQueryParam("filter", equalTo("externalId eq \"claude:reviewer@v1\""))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(scimResponse("did:web:example.com"))));

        assertThat(provider.didFor("claude:reviewer@v1")).isPresent();
        wm.verify(1, getRequestedFor(urlPathEqualTo("/scim/v2/Agents"))
                .withQueryParam("filter", equalTo("externalId eq \"claude:reviewer@v1\"")));
    }

    @Test
    void didFor_totalResultsZero_returnsEmpty_andCachesAbsence() {
        wm.stubFor(get(urlPathEqualTo("/scim/v2/Agents"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(EMPTY_RESPONSE)));

        assertThat(provider.didFor("claude:unknown@v1")).isEmpty();
        provider.didFor("claude:unknown@v1");
        wm.verify(1, getRequestedFor(urlPathEqualTo("/scim/v2/Agents")));
    }

    @Test
    void didFor_401_throwsAndIsNotCached() {
        wm.stubFor(get(urlPathEqualTo("/scim/v2/Agents"))
                .willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> provider.didFor("claude:reviewer@v1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authentication failed");
        assertThatThrownBy(() -> provider.didFor("claude:reviewer@v1"))
                .isInstanceOf(IllegalStateException.class);
        wm.verify(2, getRequestedFor(urlPathEqualTo("/scim/v2/Agents")));
    }

    @Test
    void didFor_404_throwsAndIsNotCached() {
        wm.stubFor(get(urlPathEqualTo("/scim/v2/Agents"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> provider.didFor("claude:reviewer@v1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("404");

        wm.stubFor(get(urlPathEqualTo("/scim/v2/Agents"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(scimResponse("did:web:example.com"))));
        assertThat(provider.didFor("claude:reviewer@v1")).isPresent();
        wm.verify(2, getRequestedFor(urlPathEqualTo("/scim/v2/Agents")));
    }

    @Test
    void didFor_totalResultsGreaterThan1_logsWarnAndUsesFirst() {
        final String multiResponse = """
            {
              "totalResults": 2,
              "Resources": [
                {"urn:ietf:params:scim:schemas:extension:casehub:2.0:Agent": {"did": "did:web:first.com"}},
                {"urn:ietf:params:scim:schemas:extension:casehub:2.0:Agent": {"did": "did:web:second.com"}}
              ]
            }
            """;
        wm.stubFor(get(urlPathEqualTo("/scim/v2/Agents"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(multiResponse)));

        assertThat(provider.didFor("claude:ambiguous@v1")).contains("did:web:first.com");
    }

    @Test
    void didFor_missingDidField_throwsAndIsNotCached() {
        wm.stubFor(get(urlPathEqualTo("/scim/v2/Agents"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"totalResults\":1,\"Resources\":[{\"urn:ietf:params:scim:schemas:extension:casehub:2.0:Agent\":{}}]}")));

        assertThatThrownBy(() -> provider.didFor("claude:reviewer@v1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing required 'did'");
        wm.verify(moreThanOrExactly(1), getRequestedFor(urlPathEqualTo("/scim/v2/Agents")));
    }

    @Test
    void onKeyRotated_invalidatesCacheForActor() {
        wm.stubFor(get(urlPathEqualTo("/scim/v2/Agents"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(scimResponse("did:web:example.com"))));

        provider.didFor("claude:reviewer@v1");
        wm.verify(1, getRequestedFor(urlPathEqualTo("/scim/v2/Agents")));

        provider.onKeyRotated(new AgentKeyRotatedEvent("claude:reviewer@v1", "oldRef", "newRef"));

        provider.didFor("claude:reviewer@v1");
        wm.verify(2, getRequestedFor(urlPathEqualTo("/scim/v2/Agents")));
    }

    @Test
    void onKeyRotated_doesNotInvalidateOtherActors() {
        wm.stubFor(get(urlPathEqualTo("/scim/v2/Agents"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(scimResponse("did:web:example.com"))));

        provider.didFor("claude:actor1@v1");
        provider.didFor("claude:actor2@v1");
        provider.onKeyRotated(new AgentKeyRotatedEvent("claude:actor1@v1", null, null));
        provider.didFor("claude:actor1@v1");
        provider.didFor("claude:actor2@v1");
        wm.verify(3, getRequestedFor(urlPathEqualTo("/scim/v2/Agents")));
    }

    @Test
    void httpsValidation_throwsForHttpEndpoint() {
        final ScimActorDIDProvider httpProvider = new ScimActorDIDProvider(
                "http://localhost:9090", "token", 5000, Duration.ofMinutes(5));
        assertThatThrownBy(httpProvider::validateEndpoint)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must use HTTPS");
    }

    @Test
    void httpsValidation_passesForHttpsEndpoint() {
        final ScimActorDIDProvider httpsProvider = new ScimActorDIDProvider(
                "https://idp.example.com", "token", 5000, Duration.ofMinutes(5));
        assertThatCode(httpsProvider::validateEndpoint).doesNotThrowAnyException();
    }
}

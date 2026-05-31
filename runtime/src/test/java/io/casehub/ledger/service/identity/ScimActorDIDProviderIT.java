package io.casehub.ledger.service.identity;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.api.spi.identity.ActorDIDProvider;
import io.casehub.ledger.runtime.service.AgentSignature;
import io.casehub.ledger.runtime.service.KeyRotationService;
import io.casehub.ledger.runtime.service.identity.ScimActorDIDProvider;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;

/**
 * Integration test verifying {@link io.casehub.ledger.runtime.service.identity.ScimActorDIDProvider}
 * CDI wiring end-to-end inside a Quarkus container:
 *
 * <ol>
 *   <li>The {@code @Alternative} is activated via {@code quarkus.arc.selected-alternatives}.</li>
 *   <li>{@code ActorDIDProvider.didFor()} delegates to SCIM over WireMock HTTP.</li>
 *   <li>{@code KeyRotationService.recordRotation()} fires {@code AgentKeyRotatedEvent}, which
 *       {@code ScimActorDIDProvider.onKeyRotated()} observes and uses to evict its cache.</li>
 * </ol>
 *
 * <p>WireMock port is injected via {@link ScimWireMockResource} as a config override, which
 * also sets {@code casehub.ledger.agent-identity.scim.require-https=false} so the plain
 * HTTP endpoint passes validation.
 */
@QuarkusTest
@TestProfile(ScimActorDIDProviderIT.ScimProfile.class)
class ScimActorDIDProviderIT {

    public static class ScimProfile implements QuarkusTestProfile {

        @Override
        public String getConfigProfile() {
            return "scim-did-provider-test";
        }

        @Override
        public List<TestResourceEntry> testResources() {
            return List.of(new TestResourceEntry(ScimWireMockResource.class));
        }
    }

    @InjectWireMock
    WireMockServer wm;

    @Inject
    ActorDIDProvider actorDIDProvider;

    @Inject
    ScimActorDIDProvider scimProvider;

    @Inject
    KeyRotationService keyRotationService;

    private static final String ACTOR_ID = "claude:scim-it-actor@v1";
    private static final String DID = "did:web:example.com:agents:scim-it";

    private void stubScimSuccess() {
        wm.stubFor(get(urlPathEqualTo("/scim/v2/Agents"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "totalResults": 1,
                              "Resources": [{
                                "urn:ietf:params:scim:schemas:extension:casehub:2.0:Agent": {
                                  "did": "%s"
                                }
                              }]
                            }
                            """.formatted(DID))));
    }

    @BeforeEach
    void setUp() {
        wm.resetAll();
        stubScimSuccess();
        // Evict any DID cached by previous tests — ScimActorDIDProvider is ApplicationScoped
        // and its cache survives across test methods within the same Quarkus instance.
        scimProvider.invalidateAll();
    }

    @Test
    void scimProviderIsActiveAlternative() {
        // Verifies the @Alternative is selected: ActorDIDProvider resolves via SCIM.
        assertThat(actorDIDProvider.didFor(ACTOR_ID)).contains(DID);
        wm.verify(moreThanOrExactly(1), getRequestedFor(urlPathEqualTo("/scim/v2/Agents")));
    }

    @Test
    @Transactional
    void keyRotation_invalidatesScimCache() throws Exception {
        // Seed the cache — first call hits SCIM, returns DID_V1
        assertThat(actorDIDProvider.didFor(ACTOR_ID)).contains(DID);

        // Change the stub to return a different DID (simulates IdP update after rotation)
        final String updatedDid = "did:web:example.com:agents:scim-it-rotated";
        wm.resetAll();
        wm.stubFor(get(urlPathEqualTo("/scim/v2/Agents"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "totalResults": 1,
                              "Resources": [{
                                "urn:ietf:params:scim:schemas:extension:casehub:2.0:Agent": {
                                  "did": "%s"
                                }
                              }]
                            }
                            """.formatted(updatedDid))));

        // Rotate key — fires AgentKeyRotatedEvent, ScimActorDIDProvider.onKeyRotated() evicts cache
        final String keyRef = AgentSignature.signWith(
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair(), new byte[0]).keyRef();
        keyRotationService.recordRotation(ACTOR_ID, keyRef,
                AgentSignature.signWith(
                        KeyPairGenerator.getInstance("Ed25519").generateKeyPair(), new byte[0]).keyRef(),
                KeyRotationReason.SCHEDULED, Instant.now());

        // Post-rotation explicit call — cache was evicted, so this must call SCIM and return the updated DID
        assertThat(actorDIDProvider.didFor(ACTOR_ID)).contains(updatedDid);
    }
}

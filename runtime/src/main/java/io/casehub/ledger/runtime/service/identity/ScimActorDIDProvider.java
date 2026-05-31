package io.casehub.ledger.runtime.service.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.ledger.api.spi.identity.ActorDIDProvider;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.service.AgentKeyRotatedEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Resolves actorId → DID URI by querying a SCIM2 Agent endpoint.
 *
 * <p>Activated via:
 * {@code quarkus.arc.selected-alternatives=io.casehub.ledger.runtime.service.identity.ScimActorDIDProvider}
 *
 * <p>Config prefix: {@code casehub.ledger.agent-identity.scim.*}
 *
 * <p>Cache is invalidated on {@link AgentKeyRotatedEvent} via CDI {@code @Observes}.
 * HTTPS is enforced via {@code @PostConstruct} — fires at first CDI instantiation,
 * not at Quarkus boot for {@code @Alternative} beans.
 */
@ApplicationScoped
@Alternative
public class ScimActorDIDProvider
        extends AbstractCachingIdentityProvider<ScimAgentResource>
        implements ActorDIDProvider {

    private static final Logger LOG = Logger.getLogger(ScimActorDIDProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EXTENSION_KEY =
            "urn:ietf:params:scim:schemas:extension:casehub:2.0:Agent";

    private final String scimEndpoint;
    private final String authToken;
    private final int timeoutMs;
    private final boolean requireHttps;
    private final HttpClient httpClient;

    @Inject
    public ScimActorDIDProvider(final LedgerConfig config) {
        super(Duration.ofMinutes(config.agentIdentity().scim().cacheTtlMinutes()));
        this.scimEndpoint = config.agentIdentity().scim().endpoint().orElse("");
        this.authToken = config.agentIdentity().scim().authToken().orElse("");
        this.timeoutMs = config.agentIdentity().scim().timeoutMs();
        this.requireHttps = config.agentIdentity().scim().requireHttps();
        this.httpClient = buildHttpClient();
    }

    /** Required by CDI for proxy generation (normal-scoped bean). Must not be called directly. */
    protected ScimActorDIDProvider() {
        super(Duration.ZERO);
        this.scimEndpoint = null;
        this.authToken = null;
        this.timeoutMs = 0;
        this.requireHttps = true;
        this.httpClient = null;
    }

    /** Test constructor — bypasses {@code @PostConstruct}; use with {@code http://} WireMock endpoints. */
    ScimActorDIDProvider(final String endpoint, final String authToken,
                         final int timeoutMs, final Duration cacheTtl) {
        super(cacheTtl);
        this.scimEndpoint = endpoint;
        this.authToken = authToken;
        this.timeoutMs = timeoutMs;
        this.requireHttps = true;
        this.httpClient = buildHttpClient();
    }

    @PostConstruct
    void validateEndpoint() {
        if (scimEndpoint == null || scimEndpoint.isBlank()) {
            throw new IllegalArgumentException(
                    "casehub.ledger.agent-identity.scim.endpoint must be configured");
        }
        if (requireHttps && !scimEndpoint.startsWith("https://")) {
            throw new IllegalArgumentException(
                    "casehub.ledger.agent-identity.scim.endpoint must use HTTPS, got: " + scimEndpoint);
        }
    }

    @Override
    public Optional<String> didFor(final String actorId) {
        return get(actorId).map(ScimAgentResource::did);
    }

    @Override
    protected Optional<ScimAgentResource> loadContext(final String actorId) {
        final String encodedActorId = URLEncoder.encode(actorId, StandardCharsets.UTF_8)
                .replace("+", "%20");
        final String url = scimEndpoint + "/scim/v2/Agents?filter=externalId%20eq%20%22"
                + encodedActorId + "%22";

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", "Bearer " + authToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        final HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (final Exception e) {
            LOG.warnf("SCIM request failed for actorId %s: %s", actorId, e.getMessage());
            throw new IllegalStateException("SCIM request failed for actorId: " + actorId, e);
        }

        return switch (response.statusCode()) {
            case 200 -> parseResponse(actorId, response.body());
            case 401 -> {
                LOG.warnf("SCIM authentication failed (HTTP 401) for actorId: %s", actorId);
                throw new IllegalStateException(
                        "SCIM authentication failed (HTTP 401) for actorId: " + actorId);
            }
            case 404 -> {
                LOG.warnf("SCIM endpoint returned 404 — possible misconfiguration: %s", scimEndpoint);
                throw new IllegalStateException(
                        "SCIM endpoint returned 404 — possible misconfiguration: " + scimEndpoint);
            }
            default -> {
                LOG.warnf("SCIM returned unexpected status %d for actorId %s",
                        response.statusCode(), actorId);
                throw new IllegalStateException(
                        "SCIM returned unexpected status " + response.statusCode()
                        + " for actorId: " + actorId);
            }
        };
    }

    void onKeyRotated(@Observes final AgentKeyRotatedEvent event) {
        invalidate(event.actorId());
    }

    private Optional<ScimAgentResource> parseResponse(final String actorId, final String body) {
        try {
            final JsonNode root = MAPPER.readTree(body);
            final int totalResults = root.path("totalResults").asInt(0);
            if (totalResults == 0) {
                return Optional.empty();
            }
            if (totalResults > 1) {
                LOG.warnf("SCIM returned %d results for externalId %s — externalId should be unique; using first result",
                        totalResults, actorId);
            }
            final JsonNode resource = root.path("Resources").get(0);
            final JsonNode extension = resource.path(EXTENSION_KEY);
            final String did = extension.path("did").asText(null);
            if (did == null || did.isBlank()) {
                throw new IllegalStateException(
                        "SCIM resource for actorId " + actorId + " is missing required 'did' field");
            }
            return Optional.of(new ScimAgentResource(did));
        } catch (final IllegalStateException e) {
            throw e;
        } catch (final Exception e) {
            LOG.warnf("Failed to parse SCIM response for actorId %s: %s", actorId, e.getMessage());
            throw new IllegalStateException("Failed to parse SCIM response for actorId: " + actorId, e);
        }
    }

    private HttpClient buildHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }
}

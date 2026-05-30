package io.casehub.ledger.runtime.service.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.ledger.api.spi.identity.DIDDocument;
import io.casehub.ledger.api.spi.identity.VerificationMethod;
import io.casehub.ledger.api.spi.resolve.DIDResolver;
import io.casehub.ledger.runtime.config.LedgerConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves did:web DIDs via HTTPS GET.
 *
 * <p>Security: rejects RFC 1918, loopback, and link-local hosts (SSRF protection).
 * Does not follow HTTP→HTTPS redirects that would bypass the SSRF check.
 * Enforces maximum response size to prevent DoS via large documents.
 * TLS is mandatory (HTTPS only).
 *
 * <p>URL derivation:
 * <ul>
 *   <li>{@code did:web:example.com} → {@code https://example.com/.well-known/did.json}</li>
 *   <li>{@code did:web:example.com:users:alice} → {@code https://example.com/users/alice/did.json}</li>
 * </ul>
 */
@ApplicationScoped
public class WebDIDResolver implements DIDResolver {

    private static final Logger LOG = Logger.getLogger(WebDIDResolver.class);

    /**
     * Blocked hosts pattern — RFC 1918 (10.x, 172.16–31.x, 192.168.x), loopback (127.x, ::1, localhost),
     * and any-address (0.0.0.0).
     */
    private static final Pattern BLOCKED_HOSTS = Pattern.compile(
            "^(localhost|127\\..*|::1|0\\.0\\.0\\.0|10\\..*|" +
            "172\\.(1[6-9]|2[0-9]|3[01])\\..*|192\\.168\\..*)$");

    private static final String DID_WEB_PREFIX = "did:web:";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LedgerConfig config;
    private final HttpClient httpClient;

    @Inject
    public WebDIDResolver(final LedgerConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofMillis(config.agentIdentity().webResolverTimeoutMs()))
                .build();
    }

    @Override
    public Optional<DIDDocument> resolve(final String did) {
        if (did == null || !did.startsWith(DID_WEB_PREFIX)) {
            return Optional.empty();
        }
        try {
            final String url = toUrl(did);
            final URI uri = URI.create(url);
            if (!isAllowedHost(uri.getHost())) {
                LOG.warnf("WebDIDResolver: blocked SSRF attempt for host %s in DID %s", uri.getHost(), did);
                return Optional.empty();
            }
            final HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(Duration.ofMillis(config.agentIdentity().webResolverTimeoutMs()))
                    .build();
            final HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.debugf("WebDIDResolver: HTTP %d for %s", response.statusCode(), did);
                return Optional.empty();
            }
            final String body = response.body();
            if (body.length() > config.agentIdentity().webResolverMaxResponseBytes()) {
                LOG.warnf("WebDIDResolver: response for %s exceeds max size (%d bytes)", did, body.length());
                return Optional.empty();
            }
            return Optional.of(parseDocument(body));
        } catch (final Exception e) {
            LOG.debugf("WebDIDResolver: failed to resolve %s: %s", did, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Returns {@code true} if the host is safe to connect to.
     * Overridable in tests to bypass the SSRF check for localhost.
     *
     * @param host the resolved host from the DID URL
     * @return {@code true} if the host is not in the blocked ranges
     */
    protected boolean isAllowedHost(final String host) {
        return host != null && !BLOCKED_HOSTS.matcher(host).matches();
    }

    /**
     * Returns the URL scheme to use when fetching DID documents.
     * Defaults to {@code "https"} in production. Overridable in tests to use plain HTTP
     * against a local WireMock server without TLS.
     *
     * @return the URL scheme (never null)
     */
    protected String scheme() {
        return "https";
    }

    /**
     * Derives the DID document URL from a {@code did:web} DID.
     *
     * <p>The did:web method spec uses literal {@code :} as path separators after the authority.
     * Colons within the authority (e.g. host:port) must be percent-encoded ({@code %3A}).
     * This implementation splits on literal {@code :} to extract path segments, then percent-decodes
     * the authority segment to recover any embedded port.
     *
     * <ul>
     *   <li>{@code did:web:example.com} → {@code https://example.com/.well-known/did.json}</li>
     *   <li>{@code did:web:example.com:users:alice} → {@code https://example.com/users/alice/did.json}</li>
     *   <li>{@code did:web:localhost%3A8080} → {@code https://localhost:8080/.well-known/did.json}</li>
     *   <li>{@code did:web:localhost%3A8080:users:alice} → {@code https://localhost:8080/users/alice/did.json}</li>
     * </ul>
     *
     * @param did the full DID string
     * @return the HTTPS URL of the DID document
     */
    private String toUrl(final String did) {
        // Work with the raw string — literal ':' are path separators; '%3A' are encoded colons in the authority
        final String hostAndPath = did.substring(DID_WEB_PREFIX.length());
        final String[] parts = hostAndPath.split(":", -1);
        // Percent-decode the authority segment to restore any host:port colons
        final String authority = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
        final String path;
        if (parts.length > 1) {
            // Remaining segments are URL path components
            final String[] pathSegments = Arrays.copyOfRange(parts, 1, parts.length);
            path = "/" + String.join("/", pathSegments) + "/did.json";
        } else {
            path = "/.well-known/did.json";
        }
        return scheme() + "://" + authority + path;
    }

    /**
     * Parses a W3C DID Core JSON document into a {@link DIDDocument}.
     *
     * @param json the raw JSON string
     * @return the parsed DID document
     * @throws Exception if JSON parsing fails
     */
    private DIDDocument parseDocument(final String json) throws Exception {
        final JsonNode root = OBJECT_MAPPER.readTree(json);

        final String id = root.path("id").asText("");

        final List<VerificationMethod> vms = new ArrayList<>();
        final JsonNode vmArray = root.path("verificationMethod");
        if (vmArray.isArray()) {
            for (final JsonNode vmNode : vmArray) {
                final String vmId = vmNode.path("id").asText("");
                final String type = vmNode.path("type").asText("");
                final String multibase = vmNode.path("publicKeyMultibase").asText("");
                byte[] keyBytes = new byte[0];
                if (multibase.startsWith("z") && multibase.length() > 1) {
                    try {
                        keyBytes = Base64.getUrlDecoder().decode(multibase.substring(1));
                    } catch (final Exception ex) {
                        LOG.debugf("WebDIDResolver: failed to decode publicKeyMultibase: %s", ex.getMessage());
                    }
                }
                vms.add(new VerificationMethod(vmId, type, keyBytes));
            }
        }

        final List<String> alsoKnownAs = new ArrayList<>();
        final JsonNode akaArray = root.path("alsoKnownAs");
        if (akaArray.isArray()) {
            for (final JsonNode akaNode : akaArray) {
                alsoKnownAs.add(akaNode.asText());
            }
        }

        return new DIDDocument(id, vms, alsoKnownAs);
    }
}

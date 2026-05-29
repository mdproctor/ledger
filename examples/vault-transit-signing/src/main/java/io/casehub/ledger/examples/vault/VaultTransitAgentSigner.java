package io.casehub.ledger.examples.vault;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.casehub.ledger.runtime.service.AbstractCachingAgentSigner;
import io.casehub.ledger.runtime.service.AgentSignature;
import io.casehub.ledger.runtime.service.LedgerPemUtil;
import io.quarkus.scheduler.Scheduled;

/**
 * {@link io.casehub.ledger.runtime.service.AgentSigner} that delegates signing to
 * HashiCorp Vault Transit Secrets Engine.
 *
 * <p>The private key never leaves Vault. Only the public key is fetched and cached locally
 * (for storage on {@code LedgerEntry.agentPublicKey}, needed by {@code AgentCryptographicVerifier}).
 *
 * <p><strong>Auth:</strong> This example uses a static Vault token
 * ({@code casehub.ledger.vault-transit.token}). Production deployments should use
 * AppRole or OIDC. See issue #101.
 *
 * <p><strong>Algorithm support:</strong> Only {@code ed25519} Vault Transit key types are
 * supported. Vault returns a 64-byte raw signature prefixed with {@code vault:v1:} in
 * base64. This class strips the prefix and decodes to raw bytes — the format JCA expects
 * for Ed25519 verification.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class VaultTransitAgentSigner extends AbstractCachingAgentSigner<VaultTransitContext> {

    private static final Logger LOG = Logger.getLogger(VaultTransitAgentSigner.class);
    private static final String VAULT_V1_PREFIX = "vault:v1:";

    private final VaultTransitConfig config;
    private final HttpClient http;
    private final ObjectMapper mapper;

    @Inject
    public VaultTransitAgentSigner(final VaultTransitConfig config) {
        this.config = config;
        this.http = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    @Override
    protected Optional<VaultTransitContext> loadContext(final String actorId) {
        final Map<String, String> keyMapping = config.keyMapping();
        final String keyName = keyMapping.get(actorId);
        if (keyName == null) {
            LOG.debugf("No Vault Transit key configured for actor %s — skipping signing", actorId);
            return Optional.empty();
        }
        final PublicKey publicKey = fetchPublicKey(keyName);
        return Optional.of(new VaultTransitContext(keyName, publicKey));
    }

    @Override
    protected AgentSignature performSign(final String actorId, final VaultTransitContext context,
            final byte[] data) {
        try {
            final byte[] sigBytes = callVaultSign(context.keyName(), data);
            final byte[] pubEncoded = context.publicKey().getEncoded();
            final byte[] hash = MessageDigest.getInstance("SHA-256").digest(pubEncoded);
            final String keyRef = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return new AgentSignature(sigBytes, pubEncoded, keyRef);
        } catch (final Exception e) {
            throw new RuntimeException("Vault Transit signing failed for actor " + actorId, e);
        }
    }

    @Scheduled(every = "${casehub.ledger.vault-transit.refresh-interval:5m}")
    void refreshCache() {
        LOG.debug("Invalidating Vault Transit context cache");
        invalidateAll();
    }

    /**
     * Fetches the current public key from Vault Transit.
     * Parses {@code /v1/transit/keys/<keyName>} → {@code data.keys.1.public_key} (PEM).
     */
    private PublicKey fetchPublicKey(final String keyName) {
        try {
            final HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.address() + "/v1/transit/keys/" + keyName))
                    .header("X-Vault-Token", config.token())
                    .GET()
                    .build();
            final HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new RuntimeException("Vault returned HTTP " + resp.statusCode()
                        + " fetching key info for " + keyName + ": " + resp.body());
            }
            final JsonNode root = mapper.readTree(resp.body());
            // keys is a map of version → key info; latest is the highest integer key
            final JsonNode keys = root.path("data").path("keys");
            final JsonNode latestKey = keys.fields().next().getValue();
            final String pem = latestKey.path("public_key").asText();
            return LedgerPemUtil.parsePublicKey(pem);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to fetch public key from Vault for key " + keyName, e);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to fetch public key from Vault for key " + keyName, e);
        }
    }

    /**
     * Calls {@code POST /v1/transit/sign/<keyName>} with the base64-encoded data.
     * Returns the raw signature bytes (strips the {@code vault:v1:} prefix and base64-decodes).
     */
    private byte[] callVaultSign(final String keyName, final byte[] data) throws IOException,
            InterruptedException {
        final String inputB64 = Base64.getEncoder().encodeToString(data);
        final String body = "{\"input\":\"" + inputB64 + "\"}";
        final HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(config.address() + "/v1/transit/sign/" + keyName))
                .header("X-Vault-Token", config.token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        final HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Vault Transit sign returned HTTP " + resp.statusCode()
                    + " for key " + keyName + ": " + resp.body());
        }
        final JsonNode root = mapper.readTree(resp.body());
        final String vaultSig = root.path("data").path("signature").asText();
        if (!vaultSig.startsWith(VAULT_V1_PREFIX)) {
            throw new RuntimeException("Unexpected Vault signature format: " + vaultSig);
        }
        return Base64.getDecoder().decode(vaultSig.substring(VAULT_V1_PREFIX.length()));
    }
}

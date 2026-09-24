package io.casehub.ledger.signing.vault;

import java.net.http.HttpClient;
import java.security.PublicKey;
import java.time.Clock;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.casehub.ledger.core.signing.AbstractCachingAgentSigner;
import io.casehub.ledger.core.signing.AgentSignature;

public class VaultTransitAgentSignerCore extends AbstractCachingAgentSigner<VaultTransitContext> {

    private static final Logger LOG = Logger.getLogger(VaultTransitAgentSignerCore.class.getName());

    private final VaultTransitSigningClient client;
    private final VaultTransitSigningConfig signingConfig;
    private final VaultTokenSource tokenSource;

    protected VaultTransitAgentSignerCore() {
        this.signingConfig = null;
        this.client = null;
        this.tokenSource = null;
    }

    public VaultTransitAgentSignerCore(final VaultTransitSigningConfig signingConfig,
            final VaultTransitAuthConfig authConfig) {
        this(signingConfig, authConfig, HttpClient.newBuilder().build(), new ObjectMapper(), Clock.systemUTC());
    }

    public VaultTransitAgentSignerCore(final VaultTransitSigningConfig signingConfig,
            final VaultTransitAuthConfig authConfig,
            final HttpClient httpClient, final ObjectMapper objectMapper, final Clock clock) {
        this.signingConfig = signingConfig;
        this.tokenSource = createTokenSource(authConfig, signingConfig.address(), httpClient, objectMapper, clock);
        this.client = new VaultTransitSigningClient(signingConfig, httpClient, objectMapper);
    }

    protected VaultTransitAgentSignerCore(final VaultTransitSigningConfig signingConfig,
            final VaultTransitSigningClient client, final VaultTokenSource tokenSource) {
        this.signingConfig = signingConfig;
        this.client = client;
        this.tokenSource = tokenSource;
    }

    static VaultTokenSource createTokenSource(final VaultTransitAuthConfig authConfig,
            final String address, final HttpClient httpClient, final ObjectMapper objectMapper,
            final Clock clock) {
        return switch (authConfig.method()) {
            case TOKEN -> {
                final String token = authConfig.token()
                        .orElseThrow(() -> new IllegalStateException(
                                "casehub.ledger.vault-transit.auth.token required when auth.method=token"));
                yield new StaticVaultTokenSource(token);
            }
            case APPROLE -> {
                final String roleId = authConfig.roleId()
                        .orElseThrow(() -> new IllegalStateException(
                                "casehub.ledger.vault-transit.auth.role-id required when auth.method=approle"));
                final String secretId = authConfig.secretId()
                        .orElseThrow(() -> new IllegalStateException(
                                "casehub.ledger.vault-transit.auth.secret-id required when auth.method=approle"));
                final String mountPath = authConfig.mountPath().orElse("approle");
                yield new AppRoleVaultTokenSource(address, roleId, secretId, mountPath,
                        httpClient, objectMapper, clock);
            }
            case KUBERNETES -> {
                final String role = authConfig.role()
                        .orElseThrow(() -> new IllegalStateException(
                                "casehub.ledger.vault-transit.auth.role required when auth.method=kubernetes"));
                final java.nio.file.Path jwtPath = java.nio.file.Path.of(
                        authConfig.jwtPath().orElse("/var/run/secrets/kubernetes.io/serviceaccount/token"));
                final String mountPath = authConfig.mountPath().orElse("kubernetes");
                yield JwtVaultTokenSource.fromFile(address, role, jwtPath, mountPath,
                        httpClient, objectMapper, clock);
            }
            case JWT -> {
                final String role = authConfig.role()
                        .orElseThrow(() -> new IllegalStateException(
                                "casehub.ledger.vault-transit.auth.role required when auth.method=jwt"));
                final String mountPath = authConfig.mountPath().orElse("jwt");
                if (authConfig.jwtPath().isPresent() && authConfig.jwt().isPresent()) {
                    throw new IllegalStateException(
                            "casehub.ledger.vault-transit.auth: specify jwt-path or jwt, not both");
                }
                if (authConfig.jwtPath().isPresent()) {
                    yield JwtVaultTokenSource.fromFile(address, role,
                            java.nio.file.Path.of(authConfig.jwtPath().get()), mountPath,
                            httpClient, objectMapper, clock);
                }
                final String jwt = authConfig.jwt()
                        .orElseThrow(() -> new IllegalStateException(
                                "casehub.ledger.vault-transit.auth.jwt or auth.jwt-path required when auth.method=jwt"));
                yield new JwtVaultTokenSource(address, role, () -> jwt, mountPath,
                        httpClient, objectMapper, clock);
            }
        };
    }

    @Override
    protected Optional<VaultTransitContext> loadContext(final String actorId) {
        final String keyName = signingConfig.keyMapping().get(actorId);
        if (keyName == null) {
            LOG.log(Level.FINE, "No Vault Transit key configured for actor {0} — skipping signing", actorId);
            return Optional.empty();
        }

        try {
            final String token = tokenSource.token();
            final PublicKey publicKey = client.fetchPublicKey(token, keyName);
            return Optional.of(new VaultTransitContext(keyName, publicKey));
        } catch (final VaultAuthenticationException e) {
            LOG.log(Level.FINE, "Vault 403 on key fetch for {0} — invalidating token and retrying", actorId);
            tokenSource.invalidate();
            final String freshToken = tokenSource.token();
            final PublicKey publicKey = client.fetchPublicKey(freshToken, keyName);
            return Optional.of(new VaultTransitContext(keyName, publicKey));
        }
    }

    @Override
    protected AgentSignature performSign(final String actorId, final VaultTransitContext context,
            final byte[] data) {
        try {
            final String token = tokenSource.token();
            final byte[] sigBytes = client.sign(token, context.keyName(), data);
            final byte[] pubEncoded = context.publicKey().getEncoded();
            final String keyRef = AgentSignature.computeKeyRef(pubEncoded);
            return new AgentSignature(sigBytes, pubEncoded, keyRef);
        } catch (final VaultAuthenticationException e) {
            LOG.log(Level.FINE, "Vault 403 on sign for {0} — invalidating token and retrying", actorId);
            tokenSource.invalidate();
            final String freshToken = tokenSource.token();
            final byte[] sigBytes = client.sign(freshToken, context.keyName(), data);
            final byte[] pubEncoded = context.publicKey().getEncoded();
            final String keyRef = AgentSignature.computeKeyRef(pubEncoded);
            return new AgentSignature(sigBytes, pubEncoded, keyRef);
        }
    }

    @Override
    protected PublicKey contextPublicKey(final VaultTransitContext context) {
        return context.publicKey();
    }
}

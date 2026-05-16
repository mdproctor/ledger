package io.casehub.ledger.runtime.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.arc.DefaultBean;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.casehub.ledger.runtime.config.LedgerConfig;

/**
 * Default {@link AgentKeyProvider} — loads Ed25519 key pairs from PEM files
 * configured under {@code casehub.ledger.agent-signing.keys.*}.
 *
 * <p>
 * Returns {@link Optional#empty()} for any actorId not present in config,
 * making signing effectively opt-in per actor.
 */
@DefaultBean
@ApplicationScoped
public class ConfiguredAgentKeyProvider implements AgentKeyProvider {

    private static final Logger LOG = Logger.getLogger(ConfiguredAgentKeyProvider.class);

    @Inject
    LedgerConfig config;

    private final Map<String, KeyPair> keyPairs = new ConcurrentHashMap<>();

    @PostConstruct
    void loadKeys() {
        config.agentSigning().keys().forEach((actorId, keyConfig) -> {
            try {
                final PrivateKey priv = loadPrivateKey(keyConfig.privateKey());
                final PublicKey pub = loadPublicKey(keyConfig.publicKey());
                keyPairs.put(actorId, new KeyPair(pub, priv));
                LOG.infof("Loaded signing key pair for actor: %s", actorId);
            } catch (final Exception e) {
                LOG.errorf("Failed to load signing key for actor %s: %s", actorId, e.getMessage());
            }
        });
    }

    @Override
    public Optional<KeyPair> signingKeyPair(final String actorId) {
        return Optional.ofNullable(keyPairs.get(actorId));
    }

    private static PrivateKey loadPrivateKey(final String pemPath) throws Exception {
        final String pem = Files.readString(Path.of(pemPath));
        final byte[] keyBytes = decodePem(pem, "PRIVATE KEY");
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private static PublicKey loadPublicKey(final String pemPath) throws Exception {
        final String pem = Files.readString(Path.of(pemPath));
        final byte[] keyBytes = decodePem(pem, "PUBLIC KEY");
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    private static byte[] decodePem(final String pem, final String type) {
        return Base64.getDecoder().decode(
            pem.replace("-----BEGIN " + type + "-----", "")
               .replace("-----END " + type + "-----", "")
               .replaceAll("\\s", ""));
    }
}

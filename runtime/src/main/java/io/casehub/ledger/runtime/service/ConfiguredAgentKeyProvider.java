package io.casehub.ledger.runtime.service;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkus.arc.DefaultBean;

import io.casehub.ledger.runtime.config.LedgerConfig;

/**
 * Default {@link AgentKeyProvider} — loads Ed25519 key pairs from PEM files
 * configured under {@code casehub.ledger.agent-signing.keys.*}.
 *
 * <p>
 * Returns {@link Optional#empty()} for any actorId not present in config,
 * making signing effectively opt-in per actor. Logs a warning at signing time
 * when an actor was configured but failed to load — so unsigned entries are
 * never silent operational failures.
 */
@DefaultBean
@ApplicationScoped
public class ConfiguredAgentKeyProvider implements AgentKeyProvider {

    private static final Logger LOG = Logger.getLogger(ConfiguredAgentKeyProvider.class);

    @Inject
    LedgerConfig config;

    private final Map<String, SigningKey> signingKeys = new ConcurrentHashMap<>();
    private final Set<String> failedActors = ConcurrentHashMap.newKeySet();

    @PostConstruct
    void loadKeys() {
        config.agentSigning().keys().forEach((actorId, keyConfig) -> {
            try {
                final PrivateKey priv = LedgerPemUtil.loadPrivateKey(keyConfig.privateKey());
                final PublicKey pub = LedgerPemUtil.loadPublicKey(keyConfig.publicKey());
                final SigningKey signingKey = SigningKey.of(new KeyPair(pub, priv));
                signingKeys.put(actorId, signingKey);
                LOG.infof("Loaded signing key for actor %s — keyRef: %s", actorId, signingKey.keyRef());
            } catch (final Exception e) {
                failedActors.add(actorId);
                LOG.errorf("Failed to load signing key for actor %s: %s — entries for this actor will be unsigned",
                        actorId, e.getMessage());
            }
        });
    }

    @Override
    public Optional<SigningKey> signingKey(final String actorId) {
        if (failedActors.contains(actorId)) {
            LOG.warnf("Actor %s was configured for signing but key failed to load — entry will be unsigned", actorId);
        }
        return Optional.ofNullable(signingKeys.get(actorId));
    }

}

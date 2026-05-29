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
 * Default {@link AgentSigner} — loads key pairs from PEM files configured under
 * {@code casehub.ledger.agent-signing.keys.*} at startup.
 *
 * <p>Signing is opt-in per actor. Actors without a configured key pair produce unsigned entries.
 * Actors whose key files fail to load at startup are tracked in {@code failedActors}:
 * a single error is logged at startup, and subsequent {@link #sign} calls return empty without
 * further logging — no per-call log storm for misconfigured actors.
 */
@DefaultBean
@ApplicationScoped
public class ConfiguredAgentSigner implements AgentSigner {

    private static final Logger LOG = Logger.getLogger(ConfiguredAgentSigner.class);

    private final LedgerConfig config;
    private final Map<String, KeyPair> signingKeys = new ConcurrentHashMap<>();
    private final Set<String> failedActors = ConcurrentHashMap.newKeySet();

    @Inject
    public ConfiguredAgentSigner(final LedgerConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void loadKeys() {
        config.agentSigning().keys().forEach((actorId, keyConfig) -> {
            try {
                final PrivateKey priv = LedgerPemUtil.loadPrivateKey(keyConfig.privateKey());
                final PublicKey pub = LedgerPemUtil.loadPublicKey(keyConfig.publicKey());
                signingKeys.put(actorId, new KeyPair(pub, priv));
                LOG.infof("Loaded signing key for actor %s", actorId);
            } catch (final Exception e) {
                failedActors.add(actorId);
                LOG.errorf("Failed to load signing key for actor %s: %s — entries will be unsigned",
                        actorId, e.getMessage());
            }
        });
    }

    @Override
    public Optional<AgentSignature> sign(final String actorId, final byte[] data) {
        if (failedActors.contains(actorId)) {
            return Optional.empty();
        }
        final KeyPair kp = signingKeys.get(actorId);
        if (kp == null) {
            return Optional.empty();
        }
        return Optional.of(AgentSignature.signWith(kp, data));
    }
}

package io.casehub.ledger.signing.azure;

import java.security.PublicKey;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.casehub.ledger.core.signing.AbstractCachingAgentSigner;
import io.casehub.ledger.core.signing.AgentSignature;

public class AzureKeyVaultAgentSignerCore extends AbstractCachingAgentSigner<AzureKeyVaultContext> {

    private static final Logger LOG = Logger.getLogger(AzureKeyVaultAgentSignerCore.class.getName());

    private final AzureKeyVaultSigningClient client;
    private final Map<String, String> keyMapping;

    protected AzureKeyVaultAgentSignerCore() {
        this.keyMapping = null;
        this.client = null;
    }

    public AzureKeyVaultAgentSignerCore(final Map<String, String> keyMapping) {
        this.keyMapping = keyMapping;
        this.client = new AzureKeyVaultSigningClient();
    }

    public AzureKeyVaultAgentSignerCore(final Map<String, String> keyMapping,
            final AzureKeyVaultClientWrapper wrapper) {
        this.keyMapping = keyMapping;
        this.client = new AzureKeyVaultSigningClient(wrapper);
    }

    protected AzureKeyVaultAgentSignerCore(final Map<String, String> keyMapping,
            final AzureKeyVaultSigningClient client) {
        this.keyMapping = keyMapping;
        this.client = client;
    }

    @Override
    protected Optional<AzureKeyVaultContext> loadContext(final String actorId) {
        final String keyRef = keyMapping.get(actorId);
        if (keyRef == null) {
            LOG.log(Level.FINE, "No Azure Key Vault key configured for actor {0} — skipping signing", actorId);
            return Optional.empty();
        }
        return client.fetchPublicKey(keyRef);
    }

    @Override
    protected AgentSignature performSign(final String actorId, final AzureKeyVaultContext context,
            final byte[] data) {
        final byte[] sigBytes = client.sign(
                context.vaultUrl(), context.keyName(), context.algorithm(), data);
        final byte[] pubEncoded = context.publicKey().getEncoded();
        final String keyRef = AgentSignature.computeKeyRef(pubEncoded);
        return new AgentSignature(sigBytes, pubEncoded, keyRef);
    }

    @Override
    protected PublicKey contextPublicKey(final AzureKeyVaultContext context) {
        return context.publicKey();
    }
}

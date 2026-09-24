package io.casehub.ledger.signing.gcp;

import java.security.PublicKey;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.casehub.ledger.core.signing.AbstractCachingAgentSigner;
import io.casehub.ledger.core.signing.AgentSignature;

public class GcpKmsAgentSignerCore extends AbstractCachingAgentSigner<GcpKmsContext> {

    private static final Logger LOG = Logger.getLogger(GcpKmsAgentSignerCore.class.getName());

    private final GcpKmsSigningClient client;
    private final Map<String, String> keyMapping;

    protected GcpKmsAgentSignerCore() {
        this.keyMapping = null;
        this.client = null;
    }

    public GcpKmsAgentSignerCore(final Map<String, String> keyMapping) {
        this.keyMapping = keyMapping;
        this.client = new GcpKmsSigningClient();
    }

    protected GcpKmsAgentSignerCore(final Map<String, String> keyMapping,
            final GcpKmsSigningClient client) {
        this.keyMapping = keyMapping;
        this.client = client;
    }

    @Override
    protected Optional<GcpKmsContext> loadContext(final String actorId) {
        final String versionName = keyMapping.get(actorId);
        if (versionName == null) {
            LOG.log(Level.FINE, "No GCP Cloud KMS key configured for actor {0} — skipping signing", actorId);
            return Optional.empty();
        }
        return Optional.ofNullable(client.fetchPublicKey(versionName));
    }

    @Override
    protected AgentSignature performSign(final String actorId, final GcpKmsContext context,
            final byte[] data) {
        final byte[] sigBytes = client.sign(context.versionName(), context.algorithm(), data);
        final byte[] pubEncoded = context.publicKey().getEncoded();
        final String keyRef = AgentSignature.computeKeyRef(pubEncoded);
        return new AgentSignature(sigBytes, pubEncoded, keyRef);
    }

    @Override
    protected PublicKey contextPublicKey(final GcpKmsContext context) {
        return context.publicKey();
    }
}

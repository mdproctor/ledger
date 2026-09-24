package io.casehub.ledger.signing.aws;

import java.security.PublicKey;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.casehub.ledger.core.signing.AbstractCachingAgentSigner;
import io.casehub.ledger.core.signing.AgentSignature;

public class AwsKmsAgentSignerCore extends AbstractCachingAgentSigner<AwsKmsContext> {

    private static final Logger LOG = Logger.getLogger(AwsKmsAgentSignerCore.class.getName());

    private final AwsKmsSigningClient client;
    private final Map<String, String> keyMapping;

    protected AwsKmsAgentSignerCore() {
        this.keyMapping = null;
        this.client = null;
    }

    public AwsKmsAgentSignerCore(final AwsKmsSigningConfig signingConfig) {
        this.keyMapping = signingConfig.keyMapping();
        this.client = new AwsKmsSigningClient(signingConfig);
    }

    public AwsKmsAgentSignerCore(final AwsKmsSigningConfig signingConfig,
            final software.amazon.awssdk.services.kms.KmsClient kmsClient) {
        this.keyMapping = signingConfig.keyMapping();
        this.client = new AwsKmsSigningClient(signingConfig, kmsClient);
    }

    protected AwsKmsAgentSignerCore(final Map<String, String> keyMapping,
            final AwsKmsSigningClient client) {
        this.keyMapping = keyMapping;
        this.client = client;
    }

    @Override
    protected Optional<AwsKmsContext> loadContext(final String actorId) {
        final String keyArn = keyMapping.get(actorId);
        if (keyArn == null) {
            LOG.log(Level.FINE, "No AWS KMS key configured for actor {0} — skipping signing", actorId);
            return Optional.empty();
        }
        final var keyInfo = client.fetchPublicKey(keyArn);
        if (keyInfo == null) {
            return Optional.empty();
        }
        return Optional.of(new AwsKmsContext(keyArn, keyInfo.publicKey(), keyInfo.signingAlgorithm()));
    }

    @Override
    protected AgentSignature performSign(final String actorId, final AwsKmsContext context,
            final byte[] data) {
        final byte[] sigBytes = client.sign(context.keyArn(), data, context.signingAlgorithm());
        final byte[] pubEncoded = context.publicKey().getEncoded();
        final String keyRef = AgentSignature.computeKeyRef(pubEncoded);
        return new AgentSignature(sigBytes, pubEncoded, keyRef);
    }

    @Override
    protected PublicKey contextPublicKey(final AwsKmsContext context) {
        return context.publicKey();
    }
}

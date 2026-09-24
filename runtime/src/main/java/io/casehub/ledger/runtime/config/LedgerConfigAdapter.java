package io.casehub.ledger.runtime.config;

import io.casehub.ledger.core.config.*;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class LedgerConfigAdapter {

    private LedgerConfigAdapter() {}

    public static LedgerProperties toProperties(LedgerConfig config) {
        return new LedgerProperties(
                config.enabled(),
                config.datasource().filter(s -> !s.isBlank()),
                new HashChainProperties(config.hashChain().enabled()),
                new DecisionContextProperties(config.decisionContext().enabled()),
                new EvidenceProperties(config.evidence().enabled()),
                new AttestationProperties(config.attestations().enabled()),
                mapTrustScore(config.trustScore()),
                new RetentionProperties(
                        config.retention().enabled(),
                        config.retention().operationalDays(),
                        config.retention().archiveBeforeDelete()),
                mapMerkle(config.merkle()),
                new IdentityProperties(
                        new IdentityProperties.TokenisationProperties(
                                config.identity().tokenisation().enabled())),
                new DecayProperties(config.decay().flaggedPersistenceMultiplier()),
                new HealthProperties(config.health().enabled(), config.health().checkInterval()),
                mapAgentSigning(config.agentSigning()),
                new OutcomeProperties(config.outcome().defaultAttestorId(), config.outcome().defaultAttestorType()),
                new ErasureReceiptProperties(config.erasureReceipt().enabled()),
                new MetadataProperties(config.metadata().maxSize()),
                mapAgentIdentity(config.agentIdentity())
        );
    }

    private static TrustScoreProperties mapTrustScore(LedgerConfig.TrustScoreConfig ts) {
        return new TrustScoreProperties(
                ts.enabled(), ts.decayHalfLifeDays(), ts.routingEnabled(), ts.routingDeltaThreshold(),
                ts.schedule(),
                new TrustScoreProperties.EigenTrustProperties(
                        ts.eigentrust().enabled(), ts.eigentrust().alpha(), ts.eigentrust().preTrustedActors()),
                new TrustScoreProperties.ExportProperties(ts.export().deploymentId()),
                new TrustScoreProperties.BootstrapProperties(ts.bootstrap().enabled()),
                new TrustScoreProperties.MaterializationProperties(ts.materialization().enabled()),
                new TrustScoreProperties.IncrementalProperties(ts.incremental().enabled()),
                new TrustScoreProperties.SnapshotProperties(ts.snapshot().retentionDays()),
                ts.aggregationStrategy()
        );
    }

    private static MerkleProperties mapMerkle(LedgerConfig.MerkleConfig m) {
        return new MerkleProperties(
                new MerkleProperties.PublishProperties(
                        m.publish().url().filter(s -> !s.isBlank()),
                        m.publish().privateKey().filter(s -> !s.isBlank()),
                        m.publish().keyId()));
    }

    private static AgentSigningProperties mapAgentSigning(LedgerConfig.AgentSigningConfig as) {
        Map<String, AgentSigningProperties.ActorKeyProperties> keys = as.keys().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new AgentSigningProperties.ActorKeyProperties(
                                e.getValue().privateKey(), e.getValue().publicKey())));
        return new AgentSigningProperties(keys);
    }

    private static AgentIdentityProperties mapAgentIdentity(LedgerConfig.AgentIdentityConfig ai) {
        return new AgentIdentityProperties(
                AgentIdentityProperties.ValidationMode.valueOf(ai.validationMode().name()),
                ai.dids(),
                ai.didResolverCacheTtlMinutes(),
                ai.credentialCacheTtlMinutes(),
                ai.webResolverTimeoutMs(),
                ai.webResolverMaxResponseBytes(),
                new AgentIdentityProperties.ScimProperties(
                        ai.scim().endpoint(), ai.scim().authToken(),
                        ai.scim().timeoutMs(), ai.scim().cacheTtlMinutes(), ai.scim().requireHttps())
        );
    }
}

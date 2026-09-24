package io.casehub.ledger.spring;

import io.casehub.ledger.core.config.*;
import io.casehub.ledger.core.trust.AttestationAggregator;
import io.casehub.platform.api.identity.ActorType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ConfigurationProperties(prefix = "casehub.ledger")
public class LedgerConfigurationProperties {

    private boolean enabled = true;
    private String datasource;
    private HashChain hashChain = new HashChain();
    private DecisionContext decisionContext = new DecisionContext();
    private Evidence evidence = new Evidence();
    private Attestation attestations = new Attestation();
    private TrustScore trustScore = new TrustScore();
    private Retention retention = new Retention();
    private Merkle merkle = new Merkle();
    private Identity identity = new Identity();
    private Decay decay = new Decay();
    private Health health = new Health();
    private AgentSigning agentSigning = new AgentSigning();
    private Outcome outcome = new Outcome();
    private ErasureReceipt erasureReceipt = new ErasureReceipt();
    private Metadata metadata = new Metadata();
    private AgentIdentity agentIdentity = new AgentIdentity();

    public LedgerProperties toProperties() {
        return new LedgerProperties(
                enabled,
                Optional.ofNullable(datasource).filter(s -> !s.isBlank()),
                new HashChainProperties(hashChain.enabled),
                new DecisionContextProperties(decisionContext.enabled),
                new EvidenceProperties(evidence.enabled),
                new AttestationProperties(attestations.enabled),
                trustScore.toProperties(),
                new RetentionProperties(retention.enabled, retention.operationalDays, retention.archiveBeforeDelete),
                merkle.toProperties(),
                new IdentityProperties(new IdentityProperties.TokenisationProperties(identity.tokenisation.enabled)),
                new DecayProperties(decay.flaggedPersistenceMultiplier),
                new HealthProperties(health.enabled, health.checkInterval),
                agentSigning.toProperties(),
                new OutcomeProperties(Optional.ofNullable(outcome.defaultAttestorId), outcome.defaultAttestorType),
                new ErasureReceiptProperties(erasureReceipt.enabled),
                new MetadataProperties(metadata.maxSize),
                agentIdentity.toProperties()
        );
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getDatasource() { return datasource; }
    public void setDatasource(String datasource) { this.datasource = datasource; }
    public HashChain getHashChain() { return hashChain; }
    public void setHashChain(HashChain hashChain) { this.hashChain = hashChain; }
    public DecisionContext getDecisionContext() { return decisionContext; }
    public void setDecisionContext(DecisionContext decisionContext) { this.decisionContext = decisionContext; }
    public Evidence getEvidence() { return evidence; }
    public void setEvidence(Evidence evidence) { this.evidence = evidence; }
    public Attestation getAttestations() { return attestations; }
    public void setAttestations(Attestation attestations) { this.attestations = attestations; }
    public TrustScore getTrustScore() { return trustScore; }
    public void setTrustScore(TrustScore trustScore) { this.trustScore = trustScore; }
    public Retention getRetention() { return retention; }
    public void setRetention(Retention retention) { this.retention = retention; }
    public Merkle getMerkle() { return merkle; }
    public void setMerkle(Merkle merkle) { this.merkle = merkle; }
    public Identity getIdentity() { return identity; }
    public void setIdentity(Identity identity) { this.identity = identity; }
    public Decay getDecay() { return decay; }
    public void setDecay(Decay decay) { this.decay = decay; }
    public Health getHealth() { return health; }
    public void setHealth(Health health) { this.health = health; }
    public AgentSigning getAgentSigning() { return agentSigning; }
    public void setAgentSigning(AgentSigning agentSigning) { this.agentSigning = agentSigning; }
    public Outcome getOutcome() { return outcome; }
    public void setOutcome(Outcome outcome) { this.outcome = outcome; }
    public ErasureReceipt getErasureReceipt() { return erasureReceipt; }
    public void setErasureReceipt(ErasureReceipt erasureReceipt) { this.erasureReceipt = erasureReceipt; }
    public Metadata getMetadata() { return metadata; }
    public void setMetadata(Metadata metadata) { this.metadata = metadata; }
    public AgentIdentity getAgentIdentity() { return agentIdentity; }
    public void setAgentIdentity(AgentIdentity agentIdentity) { this.agentIdentity = agentIdentity; }

    public static class HashChain {
        private boolean enabled = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class DecisionContext {
        private boolean enabled = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class Evidence {
        private boolean enabled = false;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class Attestation {
        private boolean enabled = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class TrustScore {
        private boolean enabled = false;
        private int decayHalfLifeDays = 90;
        private boolean routingEnabled = false;
        private double routingDeltaThreshold = 0.01;
        private String schedule = "24h";
        private EigenTrust eigentrust = new EigenTrust();
        private Export export = new Export();
        private Bootstrap bootstrap = new Bootstrap();
        private Materialization materialization = new Materialization();
        private Incremental incremental = new Incremental();
        private Snapshot snapshot = new Snapshot();
        private AttestationAggregator.Strategy aggregationStrategy = AttestationAggregator.Strategy.WEIGHTED_MAJORITY;

        TrustScoreProperties toProperties() {
            return new TrustScoreProperties(
                    enabled, decayHalfLifeDays, routingEnabled, routingDeltaThreshold, schedule,
                    new TrustScoreProperties.EigenTrustProperties(eigentrust.enabled, eigentrust.alpha, Optional.ofNullable(eigentrust.preTrustedActors)),
                    new TrustScoreProperties.ExportProperties(Optional.ofNullable(export.deploymentId)),
                    new TrustScoreProperties.BootstrapProperties(bootstrap.enabled),
                    new TrustScoreProperties.MaterializationProperties(materialization.enabled),
                    new TrustScoreProperties.IncrementalProperties(incremental.enabled),
                    new TrustScoreProperties.SnapshotProperties(snapshot.retentionDays),
                    aggregationStrategy
            );
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getDecayHalfLifeDays() { return decayHalfLifeDays; }
        public void setDecayHalfLifeDays(int decayHalfLifeDays) { this.decayHalfLifeDays = decayHalfLifeDays; }
        public boolean isRoutingEnabled() { return routingEnabled; }
        public void setRoutingEnabled(boolean routingEnabled) { this.routingEnabled = routingEnabled; }
        public double getRoutingDeltaThreshold() { return routingDeltaThreshold; }
        public void setRoutingDeltaThreshold(double routingDeltaThreshold) { this.routingDeltaThreshold = routingDeltaThreshold; }
        public String getSchedule() { return schedule; }
        public void setSchedule(String schedule) { this.schedule = schedule; }
        public EigenTrust getEigentrust() { return eigentrust; }
        public void setEigentrust(EigenTrust eigentrust) { this.eigentrust = eigentrust; }
        public Export getExport() { return export; }
        public void setExport(Export export) { this.export = export; }
        public Bootstrap getBootstrap() { return bootstrap; }
        public void setBootstrap(Bootstrap bootstrap) { this.bootstrap = bootstrap; }
        public Materialization getMaterialization() { return materialization; }
        public void setMaterialization(Materialization materialization) { this.materialization = materialization; }
        public Incremental getIncremental() { return incremental; }
        public void setIncremental(Incremental incremental) { this.incremental = incremental; }
        public Snapshot getSnapshot() { return snapshot; }
        public void setSnapshot(Snapshot snapshot) { this.snapshot = snapshot; }
        public AttestationAggregator.Strategy getAggregationStrategy() { return aggregationStrategy; }
        public void setAggregationStrategy(AttestationAggregator.Strategy aggregationStrategy) { this.aggregationStrategy = aggregationStrategy; }

        public static class EigenTrust {
            private boolean enabled = false;
            private double alpha = 0.15;
            private List<String> preTrustedActors;
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public double getAlpha() { return alpha; }
            public void setAlpha(double alpha) { this.alpha = alpha; }
            public List<String> getPreTrustedActors() { return preTrustedActors; }
            public void setPreTrustedActors(List<String> preTrustedActors) { this.preTrustedActors = preTrustedActors; }
        }

        public static class Export {
            private String deploymentId;
            public String getDeploymentId() { return deploymentId; }
            public void setDeploymentId(String deploymentId) { this.deploymentId = deploymentId; }
        }

        public static class Bootstrap {
            private boolean enabled = false;
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
        }

        public static class Materialization {
            private boolean enabled = true;
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
        }

        public static class Incremental {
            private boolean enabled = false;
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
        }

        public static class Snapshot {
            private int retentionDays = 365;
            public int getRetentionDays() { return retentionDays; }
            public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
        }
    }

    public static class Retention {
        private boolean enabled = false;
        private int operationalDays = 180;
        private boolean archiveBeforeDelete = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getOperationalDays() { return operationalDays; }
        public void setOperationalDays(int operationalDays) { this.operationalDays = operationalDays; }
        public boolean isArchiveBeforeDelete() { return archiveBeforeDelete; }
        public void setArchiveBeforeDelete(boolean archiveBeforeDelete) { this.archiveBeforeDelete = archiveBeforeDelete; }
    }

    public static class Merkle {
        private Publish publish = new Publish();

        MerkleProperties toProperties() {
            return new MerkleProperties(
                    new MerkleProperties.PublishProperties(
                            Optional.ofNullable(publish.url).filter(s -> !s.isBlank()),
                            Optional.ofNullable(publish.privateKey).filter(s -> !s.isBlank()),
                            publish.keyId));
        }

        public Publish getPublish() { return publish; }
        public void setPublish(Publish publish) { this.publish = publish; }

        public static class Publish {
            private String url;
            private String privateKey;
            private String keyId = "default";
            public String getUrl() { return url; }
            public void setUrl(String url) { this.url = url; }
            public String getPrivateKey() { return privateKey; }
            public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
            public String getKeyId() { return keyId; }
            public void setKeyId(String keyId) { this.keyId = keyId; }
        }
    }

    public static class Identity {
        private Tokenisation tokenisation = new Tokenisation();
        public Tokenisation getTokenisation() { return tokenisation; }
        public void setTokenisation(Tokenisation tokenisation) { this.tokenisation = tokenisation; }

        public static class Tokenisation {
            private boolean enabled = false;
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
        }
    }

    public static class Decay {
        private double flaggedPersistenceMultiplier = 0.5;
        public double getFlaggedPersistenceMultiplier() { return flaggedPersistenceMultiplier; }
        public void setFlaggedPersistenceMultiplier(double flaggedPersistenceMultiplier) { this.flaggedPersistenceMultiplier = flaggedPersistenceMultiplier; }
    }

    public static class Health {
        private boolean enabled = true;
        private String checkInterval = "1h";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getCheckInterval() { return checkInterval; }
        public void setCheckInterval(String checkInterval) { this.checkInterval = checkInterval; }
    }

    public static class AgentSigning {
        private Map<String, ActorKey> keys = Map.of();

        AgentSigningProperties toProperties() {
            var mapped = new java.util.LinkedHashMap<String, AgentSigningProperties.ActorKeyProperties>();
            keys.forEach((k, v) -> mapped.put(k, new AgentSigningProperties.ActorKeyProperties(v.privateKey, v.publicKey)));
            return new AgentSigningProperties(mapped);
        }

        public Map<String, ActorKey> getKeys() { return keys; }
        public void setKeys(Map<String, ActorKey> keys) { this.keys = keys; }

        public static class ActorKey {
            private String privateKey;
            private String publicKey;
            public String getPrivateKey() { return privateKey; }
            public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
            public String getPublicKey() { return publicKey; }
            public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
        }
    }

    public static class Outcome {
        private String defaultAttestorId;
        private ActorType defaultAttestorType = ActorType.SYSTEM;
        public String getDefaultAttestorId() { return defaultAttestorId; }
        public void setDefaultAttestorId(String defaultAttestorId) { this.defaultAttestorId = defaultAttestorId; }
        public ActorType getDefaultAttestorType() { return defaultAttestorType; }
        public void setDefaultAttestorType(ActorType defaultAttestorType) { this.defaultAttestorType = defaultAttestorType; }
    }

    public static class ErasureReceipt {
        private boolean enabled = false;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class Metadata {
        private int maxSize = 65536;
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
    }

    public static class AgentIdentity {
        private AgentIdentityProperties.ValidationMode validationMode = AgentIdentityProperties.ValidationMode.WARN;
        private Map<String, String> dids = Map.of();
        private int didResolverCacheTtlMinutes = 5;
        private int credentialCacheTtlMinutes = 60;
        private int webResolverTimeoutMs = 5000;
        private int webResolverMaxResponseBytes = 1048576;
        private Scim scim = new Scim();

        AgentIdentityProperties toProperties() {
            return new AgentIdentityProperties(
                    validationMode, dids,
                    didResolverCacheTtlMinutes, credentialCacheTtlMinutes,
                    webResolverTimeoutMs, webResolverMaxResponseBytes,
                    new AgentIdentityProperties.ScimProperties(
                            Optional.ofNullable(scim.endpoint), Optional.ofNullable(scim.authToken),
                            scim.timeoutMs, scim.cacheTtlMinutes, scim.requireHttps)
            );
        }

        public AgentIdentityProperties.ValidationMode getValidationMode() { return validationMode; }
        public void setValidationMode(AgentIdentityProperties.ValidationMode validationMode) { this.validationMode = validationMode; }
        public Map<String, String> getDids() { return dids; }
        public void setDids(Map<String, String> dids) { this.dids = dids; }
        public int getDidResolverCacheTtlMinutes() { return didResolverCacheTtlMinutes; }
        public void setDidResolverCacheTtlMinutes(int didResolverCacheTtlMinutes) { this.didResolverCacheTtlMinutes = didResolverCacheTtlMinutes; }
        public int getCredentialCacheTtlMinutes() { return credentialCacheTtlMinutes; }
        public void setCredentialCacheTtlMinutes(int credentialCacheTtlMinutes) { this.credentialCacheTtlMinutes = credentialCacheTtlMinutes; }
        public int getWebResolverTimeoutMs() { return webResolverTimeoutMs; }
        public void setWebResolverTimeoutMs(int webResolverTimeoutMs) { this.webResolverTimeoutMs = webResolverTimeoutMs; }
        public int getWebResolverMaxResponseBytes() { return webResolverMaxResponseBytes; }
        public void setWebResolverMaxResponseBytes(int webResolverMaxResponseBytes) { this.webResolverMaxResponseBytes = webResolverMaxResponseBytes; }
        public Scim getScim() { return scim; }
        public void setScim(Scim scim) { this.scim = scim; }

        public static class Scim {
            private String endpoint;
            private String authToken;
            private int timeoutMs = 5000;
            private int cacheTtlMinutes = 5;
            private boolean requireHttps = true;
            public String getEndpoint() { return endpoint; }
            public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
            public String getAuthToken() { return authToken; }
            public void setAuthToken(String authToken) { this.authToken = authToken; }
            public int getTimeoutMs() { return timeoutMs; }
            public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
            public int getCacheTtlMinutes() { return cacheTtlMinutes; }
            public void setCacheTtlMinutes(int cacheTtlMinutes) { this.cacheTtlMinutes = cacheTtlMinutes; }
            public boolean isRequireHttps() { return requireHttps; }
            public void setRequireHttps(boolean requireHttps) { this.requireHttps = requireHttps; }
        }
    }
}

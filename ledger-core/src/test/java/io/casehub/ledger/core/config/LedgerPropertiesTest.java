package io.casehub.ledger.core.config;

import io.casehub.ledger.core.trust.AttestationAggregator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerPropertiesTest {

    @Test
    void constructsWithDefaults() {
        var props = LedgerProperties.defaults();
        assertThat(props.enabled()).isTrue();
        assertThat(props.datasource()).isEmpty();
        assertThat(props.hashChain().enabled()).isTrue();
        assertThat(props.decisionContext().enabled()).isTrue();
        assertThat(props.evidence().enabled()).isFalse();
        assertThat(props.attestations().enabled()).isTrue();
        assertThat(props.trustScore().enabled()).isFalse();
        assertThat(props.trustScore().decayHalfLifeDays()).isEqualTo(90);
        assertThat(props.trustScore().routingEnabled()).isFalse();
        assertThat(props.trustScore().routingDeltaThreshold()).isEqualTo(0.01);
        assertThat(props.trustScore().schedule()).isEqualTo("24h");
        assertThat(props.trustScore().aggregationStrategy()).isEqualTo(AttestationAggregator.Strategy.WEIGHTED_MAJORITY);
        assertThat(props.retention().enabled()).isFalse();
        assertThat(props.retention().operationalDays()).isEqualTo(180);
        assertThat(props.retention().archiveBeforeDelete()).isTrue();
        assertThat(props.metadata().maxSize()).isEqualTo(65536);
        assertThat(props.health().enabled()).isTrue();
        assertThat(props.health().checkInterval()).isEqualTo("1h");
        assertThat(props.decay().flaggedPersistenceMultiplier()).isEqualTo(0.5);
        assertThat(props.erasureReceipt().enabled()).isFalse();
    }

    @Test
    void trustScoreNestedDefaults() {
        var ts = TrustScoreProperties.defaults();
        assertThat(ts.eigentrust().enabled()).isFalse();
        assertThat(ts.eigentrust().alpha()).isEqualTo(0.15);
        assertThat(ts.eigentrust().preTrustedActors()).isEmpty();
        assertThat(ts.export().deploymentId()).isEmpty();
        assertThat(ts.bootstrap().enabled()).isFalse();
        assertThat(ts.materialization().enabled()).isTrue();
        assertThat(ts.incremental().enabled()).isFalse();
        assertThat(ts.snapshot().retentionDays()).isEqualTo(365);
    }

    @Test
    void merkleNestedDefaults() {
        var merkle = MerkleProperties.defaults();
        assertThat(merkle.publish().url()).isEmpty();
        assertThat(merkle.publish().privateKey()).isEmpty();
        assertThat(merkle.publish().keyId()).isEqualTo("default");
    }

    @Test
    void identityNestedDefaults() {
        var id = IdentityProperties.defaults();
        assertThat(id.tokenisation().enabled()).isFalse();
    }

    @Test
    void agentIdentityNestedDefaults() {
        var ai = AgentIdentityProperties.defaults();
        assertThat(ai.validationMode()).isEqualTo(AgentIdentityProperties.ValidationMode.WARN);
        assertThat(ai.dids()).isEmpty();
        assertThat(ai.didResolverCacheTtlMinutes()).isEqualTo(5);
        assertThat(ai.credentialCacheTtlMinutes()).isEqualTo(60);
        assertThat(ai.webResolverTimeoutMs()).isEqualTo(5000);
        assertThat(ai.webResolverMaxResponseBytes()).isEqualTo(1048576);
        assertThat(ai.scim().endpoint()).isEmpty();
        assertThat(ai.scim().authToken()).isEmpty();
        assertThat(ai.scim().timeoutMs()).isEqualTo(5000);
        assertThat(ai.scim().cacheTtlMinutes()).isEqualTo(5);
        assertThat(ai.scim().requireHttps()).isTrue();
    }

    @Test
    void agentSigningDefaults() {
        var signing = AgentSigningProperties.defaults();
        assertThat(signing.keys()).isEmpty();
    }

    @Test
    void outcomeDefaults() {
        var outcome = OutcomeProperties.defaults();
        assertThat(outcome.defaultAttestorId()).isEmpty();
    }
}

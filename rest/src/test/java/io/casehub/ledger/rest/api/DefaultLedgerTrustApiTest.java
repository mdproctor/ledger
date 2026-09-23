package io.casehub.ledger.rest.api;

import io.casehub.ledger.api.view.CapabilityScoreView;
import io.casehub.ledger.api.view.TrustRoutingProfileView;
import io.casehub.ledger.api.view.TrustScoreView;
import io.casehub.ledger.core.repository.NoOpActorTrustScoreRepository;
import io.casehub.ledger.runtime.service.MaterializedTrustScoreSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultLedgerTrustApiTest {

    private DefaultLedgerTrustApi api;

    @BeforeEach
    void setUp() {
        api = new DefaultLedgerTrustApi();
        api.trustScoreSource = new MaterializedTrustScoreSource(
                new NoOpActorTrustScoreRepository());
    }

    @Test
    void trustScoreReturnsEmptyForUnknownActor() {
        final TrustScoreView view = api.trustScore("unknown-actor");
        assertThat(view.actorId()).isEqualTo("unknown-actor");
        assertThat(view.globalScore()).isEmpty();
        assertThat(view.capabilityScores()).isEmpty();
        assertThat(view.dimensionScores()).isEmpty();
    }

    @Test
    void capabilityScoreReturnsEmptyForUnknownActor() {
        final CapabilityScoreView view = api.capabilityScore(
                "unknown-actor", "test-cap");
        assertThat(view.actorId()).isEqualTo("unknown-actor");
        assertThat(view.capabilityTag()).isEqualTo("test-cap");
        assertThat(view.score()).isEmpty();
        assertThat(view.decisionCount()).isZero();
    }

    @Test
    void routingProfileReturnsEmptyForUnknownActor() {
        final TrustRoutingProfileView view = api.routingProfile(
                "unknown-actor", "test-cap");
        assertThat(view.actorId()).isEqualTo("unknown-actor");
        assertThat(view.globalScore()).isEmpty();
        assertThat(view.capabilityScore()).isEmpty();
    }
}

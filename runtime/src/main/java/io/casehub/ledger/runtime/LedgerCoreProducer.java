package io.casehub.ledger.runtime;

import io.casehub.ledger.api.spi.AttestorCredibilityPolicy;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.core.federation.NoOpTrustBootstrapSource;
import io.casehub.ledger.core.federation.NoOpTrustImportService;
import io.casehub.ledger.core.federation.TrustBootstrapSource;
import io.casehub.ledger.core.federation.TrustImportService;
import io.casehub.ledger.core.repository.NoOpLedgerEntryRepository;
import io.casehub.ledger.core.signing.AgentEntrySigner;
import io.casehub.ledger.core.signing.AgentSigner;
import io.casehub.ledger.core.trust.AllAttestationsGlobalStrategy;
import io.casehub.ledger.core.trust.AttestationAggregator;
import io.casehub.ledger.core.trust.DecayFunction;
import io.casehub.ledger.core.trust.ExponentialDecayFunction;
import io.casehub.ledger.core.trust.GlobalScoreStrategy;
import io.casehub.ledger.core.trust.NoOpAttestorCredibilityPolicy;
import io.casehub.ledger.core.trust.TrustGateService;
import io.casehub.ledger.core.trust.TrustScoreCalculator;
import io.casehub.ledger.runtime.config.LedgerConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import io.quarkus.arc.DefaultBean;

@ApplicationScoped
public class LedgerCoreProducer {

    @Produces
    @DefaultBean
    public DecayFunction exponentialDecayFunction(final LedgerConfig config) {
        return new ExponentialDecayFunction(
                config.trustScore().decayHalfLifeDays(),
                config.decay().flaggedPersistenceMultiplier());
    }

    @Produces
    @DefaultBean
    public GlobalScoreStrategy allAttestationsGlobalStrategy() {
        return new AllAttestationsGlobalStrategy();
    }

    @Produces
    @ApplicationScoped
    public TrustScoreCalculator trustScoreCalculator(final DecayFunction decayFunction,
                                                     final GlobalScoreStrategy globalStrategy,
                                                     final AttestorCredibilityPolicy credibilityPolicy) {
        return new TrustScoreCalculator(decayFunction, globalStrategy, credibilityPolicy);
    }

    @Produces
    @ApplicationScoped
    public TrustGateService trustGateService(final TrustScoreSource source) {
        return new TrustGateService(source);
    }

    @Produces
    @ApplicationScoped
    public AttestationAggregator attestationAggregator() {
        return new AttestationAggregator();
    }

    @Produces
    @ApplicationScoped
    public AgentEntrySigner agentEntrySigner(final AgentSigner signer) {
        return new AgentEntrySigner(signer);
    }

    @Produces
    @DefaultBean
    public LedgerEntryRepository noOpLedgerEntryRepository() {
        return new NoOpLedgerEntryRepository();
    }

    @Produces
    @DefaultBean
    public AttestorCredibilityPolicy noOpAttestorCredibilityPolicy() {
        return new NoOpAttestorCredibilityPolicy();
    }

    @Produces
    @DefaultBean
    public TrustImportService noOpTrustImportService() {
        return new NoOpTrustImportService();
    }

    @Produces
    @DefaultBean
    public TrustBootstrapSource noOpTrustBootstrapSource() {
        return new NoOpTrustBootstrapSource();
    }
}

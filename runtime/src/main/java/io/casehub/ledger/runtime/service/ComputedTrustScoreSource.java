package io.casehub.ledger.runtime.service;

import io.casehub.ledger.api.spi.CrossTenantLedgerEntryRepository;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.core.model.AttestationRecordedEvent;
import io.casehub.ledger.core.trust.TrustScoreCalculator;
import io.casehub.ledger.runtime.qualifier.CrossTenant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.OptionalDouble;

@ApplicationScoped
@Alternative
public class ComputedTrustScoreSource implements TrustScoreSource {

    private final io.casehub.ledger.core.trust.ComputedTrustSourceCore core;

    @Inject
    public ComputedTrustScoreSource(@CrossTenant CrossTenantLedgerEntryRepository ledgerRepo,
                                    TrustScoreCalculator calculator) {
        this.core = new io.casehub.ledger.core.trust.ComputedTrustSourceCore(ledgerRepo, calculator);
    }

    public void invalidateActor(String actorId) {core.invalidateActor(actorId);}

    void onAttestationRecorded(@Observes(during = TransactionPhase.AFTER_SUCCESS) AttestationRecordedEvent event) {
        core.invalidateActor(event.actorId());
    }

    @Override
    public OptionalDouble globalScore(String actorId) {return core.globalScore(actorId);}

    @Override
    public OptionalDouble capabilityScore(String actorId, String capabilityTag) {return core.capabilityScore(actorId, capabilityTag);}

    @Override
    public OptionalDouble dimensionScore(String actorId, String dimensionKey) {return core.dimensionScore(actorId, dimensionKey);}

    @Override
    public OptionalDouble capabilityDimensionScore(String actorId, String capabilityTag, String dimensionKey) {return core.capabilityDimensionScore(actorId, capabilityTag, dimensionKey);}

    @Override
    public int decisionCount(String actorId, String capabilityTag) {return core.decisionCount(actorId, capabilityTag);}

    @Override
    public Map<String, Double> allCapabilityScores(String actorId) {return core.allCapabilityScores(actorId);}

    @Override
    public Map<String, Double> allDimensionScores(String actorId) {return core.allDimensionScores(actorId);}

    @Override
    public Map<String, Double> qualityScores(String actorId, String capabilityTag) {return core.qualityScores(actorId, capabilityTag);}
}

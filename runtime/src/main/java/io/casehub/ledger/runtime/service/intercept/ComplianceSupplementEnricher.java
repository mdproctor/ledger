package io.casehub.ledger.runtime.service.intercept;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.runtime.model.supplement.JpaComplianceSupplement;
import io.casehub.ledger.core.enricher.LedgerEntryEnricher;

@ApplicationScoped
@Priority(35)
public class ComplianceSupplementEnricher implements LedgerEntryEnricher {

    @Inject
    ComplianceSupplementContext context;

    @Override
    public void enrich(final LedgerEntry entry) {
        if (!context.isActive()) {
            return;
        }
        final ComplianceSupplementContext.State state = context.current();
        final JpaComplianceSupplement cs = new JpaComplianceSupplement();
        cs.algorithmRef = emptyToNull(state.algorithmRef());
        cs.contestationUri = emptyToNull(state.contestationUri());
        cs.humanOverrideAvailable = state.humanOverrideAvailable();
        cs.planRef = emptyToNull(state.planRef());
        cs.rationale = emptyToNull(state.rationale());
        cs.confidenceScore = state.confidenceScore();
        cs.decisionContext = state.decisionContext();
        entry.attach(cs);
    }

    private static String emptyToNull(final String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }
}

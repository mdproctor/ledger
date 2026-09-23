package io.casehub.ledger.core.compliance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ComplianceSummary(
        int totalDecisions,
        int aiAssistedDecisions,
        int humanOverrideCount,
        Map<String, Integer> decisionsByType) {

    public static ComplianceSummary fromDecisions(final List<DecisionRecord> decisions) {
        int aiAssisted = 0;
        int humanOverride = 0;
        final Map<String, Integer> byType = new LinkedHashMap<>();

        for (final DecisionRecord d : decisions) {
            if (d.algorithmRef() != null) {
                aiAssisted++;
            }
            if (Boolean.TRUE.equals(d.humanOverrideAvailable())) {
                humanOverride++;
            }
            if (d.entryType() != null) {
                byType.merge(d.entryType(), 1, Integer::sum);
            }
        }
        return new ComplianceSummary(decisions.size(), aiAssisted, humanOverride, java.util.Collections.unmodifiableMap(byType));
    }
}

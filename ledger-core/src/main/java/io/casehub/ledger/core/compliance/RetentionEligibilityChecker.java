package io.casehub.ledger.core.compliance;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.casehub.ledger.api.model.LedgerEntry;

public final class RetentionEligibilityChecker {

    private RetentionEligibilityChecker() {
    }

    public static Map<UUID, List<LedgerEntry>> eligibleSubjects(
            final Map<UUID, List<LedgerEntry>> allBySubject,
            final Instant now,
            final int operationalDays) {

        final Instant cutoff = now.minus(operationalDays, ChronoUnit.DAYS);
        final Map<UUID, List<LedgerEntry>> eligible = new LinkedHashMap<>();

        for (final Map.Entry<UUID, List<LedgerEntry>> e : allBySubject.entrySet()) {
            final List<LedgerEntry> entries = e.getValue();
            if (entries.isEmpty()) {
                continue;
            }
            final boolean allOldEnough = entries.stream()
                    .allMatch(entry -> entry.occurredAt != null
                            && !entry.occurredAt.isAfter(cutoff));
            if (allOldEnough) {
                eligible.put(e.getKey(), entries);
            }
        }

        return eligible;
    }
}

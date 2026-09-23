package io.casehub.ledger.core.compliance;

import java.util.List;
import java.util.UUID;

public record SubjectChain(
        UUID subjectId,
        List<ChainEntry> entries,
        List<FrontierNode> frontier,
        String storedRoot) {
}

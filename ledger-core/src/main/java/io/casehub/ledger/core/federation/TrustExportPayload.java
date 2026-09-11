package io.casehub.ledger.core.federation;

import java.time.Instant;
import java.util.List;

public record TrustExportPayload(
        Instant exportedAt,
        String exportingDeployment,
        List<ActorExport> actors) {
}

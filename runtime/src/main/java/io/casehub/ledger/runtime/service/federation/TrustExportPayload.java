package io.casehub.ledger.runtime.service.federation;

import java.time.Instant;
import java.util.List;

/** Snapshot of trust scores for one or more actors, shaped for dashboard and cross-deployment use. */
public record TrustExportPayload(
        Instant exportedAt,
        String exportingDeployment,
        List<ActorExport> actors) {
}

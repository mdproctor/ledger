package io.casehub.ledger.runtime.service.federation;

import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import io.casehub.ledger.core.federation.TrustExportPayload;
import io.casehub.ledger.runtime.config.LedgerConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class TrustExportService {

    private final io.casehub.ledger.core.federation.TrustExportServiceCore core;

    @Inject
    TrustExportService(ActorTrustScoreRepository trustRepo, LedgerConfig config) {
        var exportProps = new io.casehub.ledger.core.config.TrustScoreProperties.ExportProperties(
                config.trustScore().export().deploymentId());
        this.core = new io.casehub.ledger.core.federation.TrustExportServiceCore(trustRepo, exportProps);
    }

    public TrustExportPayload exportAll(double minTrustScore) {
        return core.exportAll(minTrustScore);
    }

    public Optional<TrustExportPayload> exportActor(String actorId) {
        return core.exportActor(actorId);
    }

    public TrustExportPayload exportDelta(Instant since) {
        return core.exportDelta(since);
    }
}

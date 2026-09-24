package io.casehub.ledger.spring;

import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import io.casehub.ledger.api.spi.CrossTenantLedgerEntryRepository;
import io.casehub.ledger.api.spi.TrustScoreSnapshotRepository;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.core.config.LedgerProperties;
import io.casehub.ledger.core.event.TrustScoreEventPublisher;
import io.casehub.ledger.core.federation.TrustBootstrapServiceCore;
import io.casehub.ledger.core.federation.TrustBootstrapSource;
import io.casehub.ledger.core.federation.TrustExportServiceCore;
import io.casehub.ledger.core.federation.TrustImportService;
import io.casehub.ledger.core.trust.ComputedTrustSourceCore;
import io.casehub.ledger.core.trust.EigenTrustComputer;
import io.casehub.ledger.core.trust.MaterializedTrustSourceCore;
import io.casehub.ledger.core.trust.PerActorTrustComputerCore;
import io.casehub.ledger.core.trust.TrustScoreCalculator;
import io.casehub.ledger.core.trust.TrustScoreComputationService;
import io.casehub.ledger.core.trust.TrustScorePublisherCore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(name = "casehub.ledger.trust-score.enabled", havingValue = "true")
public class LedgerTrustConfig {

    @Bean
    TrustScorePublisherCore trustScorePublisherCore(TrustScoreEventPublisher publisher,
                                                     LedgerProperties props) {
        return new TrustScorePublisherCore(publisher, props.trustScore());
    }

    @Bean
    PerActorTrustComputerCore perActorTrustComputerCore(TrustScoreCalculator calculator,
                                                        ActorTrustScoreRepository trustRepo,
                                                        TrustScoreSnapshotRepository snapshotRepo) {
        return new PerActorTrustComputerCore(calculator, trustRepo, snapshotRepo);
    }

    @Bean
    EigenTrustComputer eigenTrustComputer(LedgerProperties props) {
        return new EigenTrustComputer(props.trustScore().eigentrust().alpha());
    }

    @Bean
    TrustScoreComputationService trustScoreComputationService(
            CrossTenantLedgerEntryRepository ledgerRepo,
            ActorTrustScoreRepository trustRepo,
            TrustScoreSnapshotRepository snapshotRepo,
            PerActorTrustComputerCore perActorComputer,
            TrustScorePublisherCore publisher,
            LedgerProperties props,
            TrustBootstrapServiceCore bootstrapService,
            EigenTrustComputer eigenTrustComputer) {
        return new TrustScoreComputationService(
                ledgerRepo, trustRepo, snapshotRepo,
                perActorComputer, publisher, props.trustScore(),
                bootstrapService, eigenTrustComputer);
    }

    @Bean
    TrustBootstrapServiceCore trustBootstrapServiceCore(TrustBootstrapSource source,
                                                        TrustImportService importService) {
        return new TrustBootstrapServiceCore(source, importService);
    }

    @Bean
    TrustExportServiceCore trustExportServiceCore(ActorTrustScoreRepository trustRepo,
                                                   LedgerProperties props) {
        return new TrustExportServiceCore(trustRepo, props.trustScore().export());
    }

    @Bean
    @ConditionalOnProperty(name = "casehub.ledger.trust-score.materialization.enabled",
            havingValue = "true", matchIfMissing = true)
    TrustScoreSource materializedTrustSource(ActorTrustScoreRepository repository) {
        return new MaterializedTrustSourceCore(repository);
    }

    @Bean
    @ConditionalOnProperty(name = "casehub.ledger.trust-score.materialization.enabled",
            havingValue = "false")
    TrustScoreSource computedTrustSource(CrossTenantLedgerEntryRepository ledgerRepo,
                                          TrustScoreCalculator calculator) {
        return new ComputedTrustSourceCore(ledgerRepo, calculator);
    }
}

package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.model.ActorTrustScoreBase;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import io.casehub.ledger.api.spi.CrossTenantLedgerEntryRepository;
import io.casehub.ledger.api.spi.TrustScoreSnapshotRepository;
import io.casehub.ledger.core.config.TrustScoreProperties;
import io.casehub.ledger.core.event.TrustScoreComputedAt;
import io.casehub.ledger.core.event.TrustScoreDeltaPayload;
import io.casehub.ledger.core.event.TrustScoreEventPublisher;
import io.casehub.ledger.core.event.TrustScoreFullPayload;
import io.casehub.ledger.core.federation.TrustBootstrapServiceCore;
import io.casehub.ledger.core.federation.TrustBootstrapSource;
import io.casehub.ledger.core.federation.TrustImportService;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrustScoreComputationServiceTest {

    @Test
    void runComputationPublishesNotification() {
        var ledgerRepo = mock(CrossTenantLedgerEntryRepository.class);
        var trustRepo = mock(ActorTrustScoreRepository.class);
        var snapshotRepo = mock(TrustScoreSnapshotRepository.class);

        var entry = new LedgerEntry() {{
            this.id = UUID.randomUUID();
            this.subjectId = UUID.randomUUID();
            this.sequenceNumber = 1;
            this.entryType = LedgerEntryType.EVENT;
            this.actorId = "actor-1";
            this.actorType = ActorType.AGENT;
            this.occurredAt = Instant.now();
        }};
        when(ledgerRepo.findAllEvents()).thenReturn(List.of(entry));
        when(ledgerRepo.findAttestationsForEntries(any())).thenReturn(Map.of());
        when(trustRepo.findAll()).thenReturn(List.of());
        when(trustRepo.findAllDetached()).thenReturn(List.of());

        var publisher = mock(TrustScoreEventPublisher.class);
        when(publisher.needsDeltaPayload()).thenReturn(false);

        var props = new TrustScoreProperties(
                false, 90, true, 0.01, "24h",
                TrustScoreProperties.EigenTrustProperties.defaults(),
                TrustScoreProperties.ExportProperties.defaults(),
                TrustScoreProperties.BootstrapProperties.defaults(),
                TrustScoreProperties.MaterializationProperties.defaults(),
                TrustScoreProperties.IncrementalProperties.defaults(),
                TrustScoreProperties.SnapshotProperties.defaults(),
                io.casehub.ledger.core.trust.AttestationAggregator.Strategy.WEIGHTED_MAJORITY
        );

        var calculator = new TrustScoreCalculator(
                new ExponentialDecayFunction(90, 1.5),
                new AllAttestationsGlobalStrategy(),
                new NoOpAttestorCredibilityPolicy());
        var perActor = new PerActorTrustComputerCore(calculator, trustRepo, snapshotRepo);

        var publisherCore = new TrustScorePublisherCore(publisher, props);
        var bootstrapSource = mock(TrustBootstrapSource.class);
        var importService = mock(TrustImportService.class);
        var bootstrap = new TrustBootstrapServiceCore(bootstrapSource, importService);
        var eigenTrust = new EigenTrustComputer(0.15);

        var service = new TrustScoreComputationService(
                ledgerRepo, trustRepo, snapshotRepo, perActor, publisherCore,
                props, bootstrap, eigenTrust);

        service.runComputation();

        verify(publisher).publishNotify(any(TrustScoreComputedAt.class));
    }

    @Test
    void runComputationHandlesEmptyLedger() {
        var ledgerRepo = mock(CrossTenantLedgerEntryRepository.class);
        var trustRepo = mock(ActorTrustScoreRepository.class);
        var snapshotRepo = mock(TrustScoreSnapshotRepository.class);

        when(ledgerRepo.findAllEvents()).thenReturn(List.of());
        when(ledgerRepo.findAttestationsForEntries(any())).thenReturn(Map.of());
        when(trustRepo.findAll()).thenReturn(List.of());
        when(trustRepo.findAllDetached()).thenReturn(List.of());

        var publisher = mock(TrustScoreEventPublisher.class);
        when(publisher.needsDeltaPayload()).thenReturn(false);

        var props = new TrustScoreProperties(
                false, 90, true, 0.01, "24h",
                TrustScoreProperties.EigenTrustProperties.defaults(),
                TrustScoreProperties.ExportProperties.defaults(),
                TrustScoreProperties.BootstrapProperties.defaults(),
                TrustScoreProperties.MaterializationProperties.defaults(),
                TrustScoreProperties.IncrementalProperties.defaults(),
                TrustScoreProperties.SnapshotProperties.defaults(),
                io.casehub.ledger.core.trust.AttestationAggregator.Strategy.WEIGHTED_MAJORITY
        );

        var calculator = new TrustScoreCalculator(
                new ExponentialDecayFunction(90, 1.5),
                new AllAttestationsGlobalStrategy(),
                new NoOpAttestorCredibilityPolicy());
        var perActor = new PerActorTrustComputerCore(calculator, trustRepo, snapshotRepo);

        var publisherCore = new TrustScorePublisherCore(publisher, props);
        var bootstrap = new TrustBootstrapServiceCore(mock(TrustBootstrapSource.class), mock(TrustImportService.class));
        var eigenTrust = new EigenTrustComputer(0.15);

        var service = new TrustScoreComputationService(
                ledgerRepo, trustRepo, snapshotRepo, perActor, publisherCore,
                props, bootstrap, eigenTrust);

        service.runComputation();

        verify(publisher).publishNotify(any(TrustScoreComputedAt.class));
    }
}

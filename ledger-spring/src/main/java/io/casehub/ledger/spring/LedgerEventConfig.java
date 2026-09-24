package io.casehub.ledger.spring;

import io.casehub.ledger.core.event.TrustScoreComputedAt;
import io.casehub.ledger.core.event.TrustScoreDeltaPayload;
import io.casehub.ledger.core.event.TrustScoreEventPublisher;
import io.casehub.ledger.core.event.TrustScoreFullPayload;
import io.casehub.ledger.core.event.TrustScoreActorUpdatedEvent;
import io.casehub.ledger.core.model.AgentKeyRotatedEvent;
import io.casehub.ledger.core.model.AttestationRecordedEvent;
import io.casehub.ledger.core.event.LedgerEventPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.util.logging.Level;
import java.util.logging.Logger;

@AutoConfiguration
public class LedgerEventConfig {

    @Bean
    TrustScoreEventPublisher trustScoreEventPublisher(ApplicationEventPublisher publisher) {
        return new SpringTrustScoreEventPublisher(publisher);
    }

    @Bean
    LedgerEventPublisher ledgerEventPublisher(ApplicationEventPublisher publisher) {
        return new SpringLedgerEventPublisher(publisher);
    }

    static class SpringTrustScoreEventPublisher implements TrustScoreEventPublisher {
        private static final Logger log = Logger.getLogger(SpringTrustScoreEventPublisher.class.getName());
        private final ApplicationEventPublisher publisher;

        SpringTrustScoreEventPublisher(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        @Override
        public void publishFull(TrustScoreFullPayload payload) {
            try { publisher.publishEvent(payload); }
            catch (Exception e) { log.log(Level.WARNING, "TrustScoreFullPayload listener failed", e); }
        }

        @Override
        public void publishDelta(TrustScoreDeltaPayload payload) {
            try { publisher.publishEvent(payload); }
            catch (Exception e) { log.log(Level.WARNING, "TrustScoreDeltaPayload listener failed", e); }
        }

        @Override
        public void publishNotify(TrustScoreComputedAt payload) {
            try { publisher.publishEvent(payload); }
            catch (Exception e) { log.log(Level.WARNING, "TrustScoreComputedAt listener failed", e); }
        }

        @Override
        public boolean needsDeltaPayload() {
            return true;
        }
    }

    static class SpringLedgerEventPublisher implements LedgerEventPublisher {
        private static final Logger log = Logger.getLogger(SpringLedgerEventPublisher.class.getName());
        private final ApplicationEventPublisher publisher;

        SpringLedgerEventPublisher(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        @Override
        public void publishKeyRotated(AgentKeyRotatedEvent event) {
            try { publisher.publishEvent(event); }
            catch (Exception e) { log.log(Level.WARNING, "AgentKeyRotatedEvent listener failed", e); }
        }

        @Override
        public void publishActorTrustUpdated(TrustScoreActorUpdatedEvent event) {
            try { publisher.publishEvent(event); }
            catch (Exception e) { log.log(Level.WARNING, "TrustScoreActorUpdatedEvent listener failed", e); }
        }

        @Override
        public void publishAttestationRecorded(AttestationRecordedEvent event) {
            try { publisher.publishEvent(event); }
            catch (Exception e) { log.log(Level.WARNING, "AttestationRecordedEvent listener failed", e); }
        }
    }
}

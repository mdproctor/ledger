package io.casehub.ledger.spring;

import io.casehub.ledger.core.config.LedgerProperties;
import io.casehub.ledger.core.trust.TrustScoreComputationService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.logging.Level;
import java.util.logging.Logger;

@AutoConfiguration
@EnableScheduling
public class LedgerSchedulingConfig {

    private static final Logger log = Logger.getLogger(LedgerSchedulingConfig.class.getName());

    @ConditionalOnBean(TrustScoreComputationService.class)
    @ConditionalOnProperty(name = "casehub.ledger.trust-score.enabled", havingValue = "true")
    static class TrustScoreScheduler {

        private final TrustScoreComputationService computationService;
        private final LedgerProperties props;

        TrustScoreScheduler(TrustScoreComputationService service, LedgerProperties props) {
            this.computationService = service;
            this.props = props;
        }

        @Scheduled(fixedDelayString = "${casehub.ledger.trust-score.schedule-ms:86400000}")
        void computeTrustScores() {
            if (!props.trustScore().enabled() || !props.trustScore().materialization().enabled()) {
                return;
            }
            try {
                computationService.runComputation();
            } catch (Exception e) {
                log.log(Level.SEVERE, "Trust score computation failed", e);
            }
        }
    }

    @ConditionalOnProperty(name = "casehub.ledger.health.enabled", havingValue = "true", matchIfMissing = true)
    static class HealthScheduler {

        private final LedgerProperties props;

        HealthScheduler(LedgerProperties props) {
            this.props = props;
        }

        @Scheduled(fixedDelayString = "${casehub.ledger.health.check-interval-ms:3600000}")
        void runHealthChecks() {
            if (!props.health().enabled()) {
                return;
            }
            log.info("Ledger health check scheduled — requires ledger-spring-jpa for full execution");
        }
    }

    @ConditionalOnProperty(name = "casehub.ledger.retention.enabled", havingValue = "true")
    static class RetentionScheduler {

        private final LedgerProperties props;

        RetentionScheduler(LedgerProperties props) {
            this.props = props;
        }

        @Scheduled(fixedDelayString = "${casehub.ledger.retention.schedule-ms:86400000}")
        void enforceRetention() {
            if (!props.retention().enabled()) {
                return;
            }
            log.info("Ledger retention scheduled — requires ledger-spring-jpa for full execution");
        }
    }
}

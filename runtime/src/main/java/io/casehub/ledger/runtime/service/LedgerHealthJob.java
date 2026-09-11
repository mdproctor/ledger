package io.casehub.ledger.runtime.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.qualifier.CrossTenant;
import io.casehub.ledger.runtime.repository.CrossTenantLedgerEntryRepository;
import io.casehub.ledger.core.model.LedgerAnomalyDetected;
import io.casehub.ledger.core.model.LedgerReconciliationMismatchDetected;
import io.casehub.ledger.core.model.LedgerSequenceGapDetected;
import io.casehub.ledger.core.model.SubjectSequenceStats;
import io.casehub.ledger.core.trust.LedgerReconciliationSource;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.scheduler.Scheduled;

/**
 * Scheduled health job that verifies audit completeness by:
 * <ol>
 * <li>Sequence gap detection — checks that per-(subject, tenant) sequence numbers are contiguous
 * (a gap indicates an entry was deleted after write).</li>
 * <li>Reconciliation — compares domain entity counts against ledger entry counts via
 * registered {@link LedgerReconciliationSource} SPI implementations.</li>
 * </ol>
 *
 * <p>
 * Gated by {@code casehub.ledger.health.enabled} (on by default). When disabled, the
 * scheduled trigger fires but immediately returns. The check interval is configurable
 * via {@code casehub.ledger.health.check-interval} (default {@code 1h}).
 *
 * <p>
 * Anomalies are signalled as {@link LedgerAnomalyDetected} CDI events — consumers observe
 * them to log, alert, or trigger remediation. No data is modified.
 *
 * <p>
 * Each check runs in its own transaction via CDI proxy self-invocation — {@link #run()}
 * is intentionally non-transactional so that the scheduler does not hold a single
 * transaction across both checks. At large ledger sizes, this prevents the full
 * {@code findSequenceStats()} result set from being held while reconciliation runs.
 *
 * <p>
 * {@link #run()} is exposed with package-accessible visibility for direct invocation in
 * integration tests where the scheduler is disabled via the {@code health-test} profile.
 */
@ApplicationScoped
@IfBuildProperty(name = "casehub.ledger.health.enabled", stringValue = "true", enableIfMissing = false)
public class LedgerHealthJob {

    private static final Logger LOG = Logger.getLogger(LedgerHealthJob.class);

    @Inject
    @CrossTenant
    CrossTenantLedgerEntryRepository crossTenantRepo;

    @Inject
    LedgerConfig config;

    @Inject
    Event<LedgerAnomalyDetected> anomalyEvent;

    @Inject
    @Any
    Instance<LedgerReconciliationSource> reconciliationSources;

    /** CDI proxy of this bean — used so each check method is intercepted by @Transactional. */
    @Inject
    LedgerHealthJob self;

    @Scheduled(every = "{casehub.ledger.health.check-interval:1h}", identity = "ledger-health-job")
    public void runHealthChecks() {
        if (!config.health().enabled()) {
            return;
        }
        run();
    }

    /**
     * Execute all health checks. Each check runs in its own transaction.
     * Exposed as {@code public} for direct invocation in integration tests.
     */
    public void run() {
        self.checkSequenceGaps();
        self.checkReconciliation();
    }

    /**
     * For each (subject, tenant) pair, verify that sequence numbers are contiguous (no gaps).
     * Gap formula: a subject with entries spanning [min, max] should have exactly
     * {@code max - min + 1} entries. A lower actual count indicates deletion after write.
     */
    @Transactional
    void checkSequenceGaps() {
        final List<SubjectSequenceStats> stats = crossTenantRepo.findSequenceStats();
        for (final SubjectSequenceStats s : stats) {
            final long expected = (long) s.max() - s.min() + 1;
            if (s.count() == expected) {
                continue;
            }
            LOG.warnf("Sequence gap for subject %s / tenant %s: expected %d entries (seq %d–%d), found %d",
                    s.subjectId(), s.tenancyId(), expected, s.min(), s.max(), s.count());
            final LedgerSequenceGapDetected payload = new LedgerSequenceGapDetected(s.subjectId(), s.tenancyId(), expected, s.count());
            anomalyEvent.fire(payload);
            anomalyEvent.fireAsync(payload)
                    .exceptionally(ex -> { LOG.debugf(ex, "LedgerAnomalyDetected async observer failed"); return null; });
        }
    }

    @Transactional
    void checkReconciliation() {
        for (final LedgerReconciliationSource source : reconciliationSources) {
            if (!source.isActive()) {
                continue;
            }
            try {
                final long domainCount = source.countDomainEntities();
                final long ledgerCount = source.countLedgerEntries();
                if (domainCount != ledgerCount) {
                    LOG.warnf("Reconciliation mismatch for %s: domain=%d, ledger=%d",
                            source.subjectType(), domainCount, ledgerCount);
                    final LedgerReconciliationMismatchDetected payload = new LedgerReconciliationMismatchDetected(
                            source.subjectType(), domainCount, ledgerCount);
                    anomalyEvent.fire(payload);
                    anomalyEvent.fireAsync(payload)
                            .exceptionally(ex -> { LOG.debugf(ex, "LedgerAnomalyDetected async observer failed"); return null; });
                }
            } catch (final Exception e) {
                LOG.errorf(e, "Reconciliation check failed for source %s — skipping", source.subjectType());
            }
        }
    }
}

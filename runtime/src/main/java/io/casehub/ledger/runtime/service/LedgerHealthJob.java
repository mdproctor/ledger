package io.casehub.ledger.runtime.service;

import java.util.List;
import java.util.UUID;

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
import io.casehub.ledger.runtime.service.model.SubjectSequenceStats;
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
 * {@link #run()} is exposed with package-accessible visibility for direct invocation in
 * integration tests where the scheduler is disabled via the {@code health-test} profile.
 */
@ApplicationScoped
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

    @Scheduled(every = "{casehub.ledger.health.check-interval:1h}", identity = "ledger-health-job")
    @Transactional
    public void runHealthChecks() {
        if (!config.health().enabled()) {
            return;
        }
        run();
    }

    /**
     * Execute all health checks. Exposed for direct invocation in integration tests.
     */
    @Transactional
    public void run() {
        checkSequenceGaps();
        checkReconciliation();
    }

    /**
     * For each (subject, tenant) pair, verify that sequence numbers are contiguous (no gaps).
     * Gap formula: a subject with entries spanning [min, max] should have exactly
     * {@code max - min + 1} entries. A lower actual count indicates deletion after write.
     */
    private void checkSequenceGaps() {
        final List<SubjectSequenceStats> stats = crossTenantRepo.findSequenceStats();
        for (final SubjectSequenceStats s : stats) {
            final long expected = (long) s.max() - s.min() + 1;
            if (s.count() == expected) {
                continue;
            }
            LOG.warnf("Sequence gap for subject %s / tenant %s: expected %d entries (seq %d–%d), found %d",
                    s.subjectId(), s.tenancyId(), expected, s.min(), s.max(), s.count());
            anomalyEvent.fire(new LedgerSequenceGapDetected(s.subjectId(), s.tenancyId(), expected, s.count()));
        }
    }

    private void checkReconciliation() {
        for (final LedgerReconciliationSource source : reconciliationSources) {
            if (!source.isActive()) {
                continue;
            }
            final long domainCount = source.countDomainEntities();
            final long ledgerCount = source.countLedgerEntries();
            if (domainCount != ledgerCount) {
                LOG.warnf("Reconciliation mismatch for %s: domain=%d, ledger=%d",
                        source.subjectType(), domainCount, ledgerCount);
                anomalyEvent.fire(new LedgerReconciliationMismatchDetected(
                        source.subjectType(), domainCount, ledgerCount));
            }
        }
    }
}

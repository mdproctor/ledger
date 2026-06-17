package io.casehub.ledger.runtime.repository.jpa;

import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.service.supplement.TestEntry;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Proves that concurrent first-inserts on a new {@code (subjectId, tenancyId)} pair do not
 * throw {@code ConstraintViolationException} on H2 2.2+ (regression test for
 * the H2 MERGE concurrent-safety bug — casehubio/ledger#148).
 *
 * <p>5 threads mirrors the AML reproduction: HTTP handler + 4 concurrent worker threads,
 * all writing to the same new {@code (caseId, DEFAULT_TENANT_ID)} pair simultaneously.
 *
 * <p>Hash chain is disabled: this test proves sequence allocation correctness only.
 * Merkle chain correctness under concurrency is covered by {@link JpaSequenceNumberConcurrentPgIT}.
 *
 * <p>Tests are NOT {@code @Transactional} — each {@code repo.save()} call from a pool
 * thread starts its own JTA transaction, which is the concurrent behaviour being proved.
 */
@QuarkusTest
@TestProfile(JpaSequenceNumberConcurrentH2IT.Profile.class)
class JpaSequenceNumberConcurrentH2IT {

    static final int THREAD_COUNT = 5;

    public static class Profile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "sequence-number-concurrent-h2-test";
        }
    }

    @Inject
    LedgerEntryRepository repo;

    @Test
    void concurrentFirstInsertsOnNewSubjectProduceUniqueContiguousSequences() throws Exception {
        final UUID subjectId = UUID.randomUUID();

        final List<LedgerEntry> saved = savesConcurrently(subjectId, THREAD_COUNT);

        assertThat(saved).hasSize(THREAD_COUNT);
        assertThat(saved.stream().map(e -> e.sequenceNumber).sorted().toList())
                .containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void concurrentFirstInsertsForDifferentTenantsAreScopeIsolated() throws Exception {
        final UUID subjectId = UUID.randomUUID();
        final String tenantA = "tenant-a";
        final String tenantB = "tenant-b";

        final ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        final CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<LedgerEntry>> futures = new ArrayList<>();

        final int half = THREAD_COUNT / 2;
        for (int i = 0; i < half; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return repo.save(newEntry(subjectId), tenantA);
            }));
        }
        for (int i = 0; i < THREAD_COUNT - half; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return repo.save(newEntry(subjectId), tenantB);
            }));
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        final List<LedgerEntry> resultsA = new ArrayList<>();
        final List<LedgerEntry> resultsB = new ArrayList<>();
        for (int i = 0; i < half; i++) {
            resultsA.add(futures.get(i).get());
        }
        for (int i = half; i < THREAD_COUNT; i++) {
            resultsB.add(futures.get(i).get());
        }

        assertThat(resultsA.stream().map(e -> e.sequenceNumber).sorted().toList())
                .containsExactly(1, 2);
        assertThat(resultsB.stream().map(e -> e.sequenceNumber).sorted().toList())
                .containsExactly(1, 2, 3);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<LedgerEntry> savesConcurrently(final UUID subjectId, final int count)
            throws Exception {
        final ExecutorService pool = Executors.newFixedThreadPool(count);
        final CountDownLatch ready = new CountDownLatch(count);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<LedgerEntry>> futures = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return repo.save(newEntry(subjectId), DEFAULT_TENANT_ID);
            }));
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        final List<LedgerEntry> results = new ArrayList<>();
        for (final Future<LedgerEntry> f : futures) {
            results.add(f.get());
        }
        return results;
    }

    private TestEntry newEntry(final UUID subjectId) {
        final TestEntry e = new TestEntry();
        e.subjectId = subjectId;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = "test-actor";
        e.actorType = ActorType.SYSTEM;
        e.actorRole = "tester";
        return e;
    }
}

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
import io.casehub.ledger.runtime.model.LedgerMerkleFrontier;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.repository.LedgerMerkleFrontierRepository;
import io.casehub.ledger.runtime.service.LedgerMerkleTree;
import io.casehub.ledger.service.supplement.TestEntry;
import io.casehub.ledger.test.PostgreSQLTestProfile;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Concurrent sequence-number and Merkle chain correctness test against real PostgreSQL.
 *
 * <p>Tests are NOT {@code @Transactional} — each {@code repo.save()} call from a pool thread
 * starts its own JTA transaction, which is the concurrent behaviour being proved.
 *
 * <p>Fresh subject UUIDs per test eliminate {@code @BeforeEach} cleanup.
 */
@QuarkusTest
@TestProfile(JpaSequenceNumberConcurrentPgIT.Profile.class)
class JpaSequenceNumberConcurrentPgIT {

    static final int THREAD_COUNT = 8;

    public static class Profile extends PostgreSQLTestProfile {
        @Override
        public String getConfigProfile() {
            return "sequence-number-test";
        }
    }

    @Inject
    LedgerEntryRepository repo;

    @Inject
    LedgerMerkleFrontierRepository frontierRepo;

    // ── helpers ───────────────────────────────────────────────────────────────

    private TestEntry newEntry(final UUID subjectId) {
        final TestEntry e = new TestEntry();
        e.subjectId = subjectId;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = "test-actor";
        e.actorType = ActorType.SYSTEM;
        e.actorRole = "tester";
        return e;
    }

    /**
     * Fires {@code count} concurrent saves for the same subjectId.
     * All futures are awaited and re-throw on any exception — proves all N saves completed.
     */
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
            results.add(f.get()); // re-throws ExecutionException on any thread failure
        }
        return results;
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void concurrentSavesHaveUniqueContiguousSequences() throws Exception {
        final UUID subjectId = UUID.randomUUID();

        savesConcurrently(subjectId, THREAD_COUNT);

        final List<LedgerEntry> entries = repo.findBySubjectId(subjectId, DEFAULT_TENANT_ID);
        assertThat(entries).hasSize(THREAD_COUNT);

        final List<Integer> seqs = entries.stream()
                .map(e -> e.sequenceNumber)
                .sorted()
                .toList();
        assertThat(seqs).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    void concurrentSavesProduceValidMerkleChain() throws Exception {
        final UUID subjectId = UUID.randomUUID();

        savesConcurrently(subjectId, THREAD_COUNT);

        // entries sorted by sequenceNumber (findBySubjectId guarantees this)
        final List<LedgerEntry> entries = repo.findBySubjectId(subjectId, DEFAULT_TENANT_ID);
        assertThat(entries).hasSize(THREAD_COUNT);

        // Build expected frontier by replaying append() in sequence order using stored digests.
        // Using entry.digest (accessible in runtime module). Fix 1's row lock guarantees
        // sequenceNumber is assigned before leafHash() is computed in the same transaction.
        List<LedgerMerkleFrontier> expectedFrontier = new ArrayList<>();
        for (final LedgerEntry e : entries) {
            expectedFrontier = LedgerMerkleTree.append(e.digest, expectedFrontier, subjectId);
        }
        final String expectedRoot = LedgerMerkleTree.treeRoot(expectedFrontier);

        // Read actual frontier inside a transaction (EntityManager requires JTA context for reads)
        final List<LedgerMerkleFrontier> actualFrontier = QuarkusTransaction.requiringNew()
                .call(() -> frontierRepo.findBySubjectId(subjectId, DEFAULT_TENANT_ID));
        final String actualRoot = LedgerMerkleTree.treeRoot(actualFrontier);

        assertThat(actualRoot).isEqualTo(expectedRoot);
    }

    @Test
    void concurrentSavesForDifferentSubjectsAreIsolated() throws Exception {
        final UUID subject1 = UUID.randomUUID();
        final UUID subject2 = UUID.randomUUID();
        final int perSubject = THREAD_COUNT / 2; // 4 each

        final ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        final CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<LedgerEntry>> futures = new ArrayList<>();

        for (int i = 0; i < perSubject; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return repo.save(newEntry(subject1), DEFAULT_TENANT_ID);
            }));
        }
        for (int i = 0; i < perSubject; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return repo.save(newEntry(subject2), DEFAULT_TENANT_ID);
            }));
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        for (final Future<LedgerEntry> f : futures) {
            f.get(); // assert all completed without exception
        }

        final List<LedgerEntry> s1 = repo.findBySubjectId(subject1, DEFAULT_TENANT_ID);
        final List<LedgerEntry> s2 = repo.findBySubjectId(subject2, DEFAULT_TENANT_ID);

        assertThat(s1).hasSize(perSubject);
        assertThat(s2).hasSize(perSubject);

        assertThat(s1.stream().map(e -> e.sequenceNumber).sorted().toList())
                .containsExactly(1, 2, 3, 4);
        assertThat(s2.stream().map(e -> e.sequenceNumber).sorted().toList())
                .containsExactly(1, 2, 3, 4);
    }
}

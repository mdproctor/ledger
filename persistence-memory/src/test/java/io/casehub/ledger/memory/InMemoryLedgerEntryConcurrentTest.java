package io.casehub.ledger.memory;

import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.model.LedgerMerkleFrontier;
import io.casehub.ledger.runtime.service.LedgerMerkleTree;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Concurrent correctness tests for {@link InMemoryLedgerEntryRepository}.
 *
 * <p>Field access on {@link LedgerEntry} goes through {@link MemoryTestEntry} accessors —
 * Quarkus bytecode enhancement makes {@code @Entity} public fields {@code protected} in the
 * augmented classloader; direct field access from outside a subclass throws
 * {@link IllegalAccessError}.
 *
 * <p>Merkle verification uses {@link LedgerMerkleTree#leafHash(LedgerEntry)} recomputation
 * rather than reading the stored {@code digest} field (inaccessible for the same reason).
 * Recomputation is also a stronger assertion: it catches any bug where the digest was
 * computed before {@code sequenceNumber} was assigned.
 *
 * <p>{@code @BeforeEach repo.clear()} resets all state (entries, attestations, sequence
 * counters, per-subject locks, Merkle frontier) between tests.
 */
@QuarkusTest
class InMemoryLedgerEntryConcurrentTest {

    static final int THREAD_COUNT = 8;

    @Inject
    InMemoryLedgerEntryRepository repo;

    /** Injected as concrete class — needed for findBySubjectId() in Merkle assertion. */
    @Inject
    InMemoryLedgerMerkleFrontierRepository frontierRepo;

    @BeforeEach
    void setUp() {
        repo.clear();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private MemoryTestEntry newEntry(final UUID subjectId) {
        return MemoryTestEntry.of(subjectId, LedgerEntryType.EVENT);
    }

    /**
     * Fires {@code count} concurrent saves for the same subjectId.
     * All futures awaited and re-thrown on any exception.
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
            results.add(f.get()); // re-throws on any thread failure
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
                .map(e -> ((MemoryTestEntry) e).getSequenceNumber())
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

        // Build expected frontier by replaying LedgerMerkleTree.leafHash(e) in sequence order.
        // Recomputation (not reading entry.digest) is required — field inaccessible due to
        // bytecode enhancement. Also stronger: catches digest-before-sequenceNumber bugs.
        List<LedgerMerkleFrontier> expectedFrontier = new ArrayList<>();
        for (final LedgerEntry e : entries) { // already sorted by sequenceNumber
            final String leafHash = LedgerMerkleTree.leafHash(e);
            expectedFrontier = LedgerMerkleTree.append(leafHash, expectedFrontier, subjectId);
        }
        final String expectedRoot = LedgerMerkleTree.treeRoot(expectedFrontier);

        // Read actual frontier via concrete class (interface would work too for findBySubjectId,
        // but concrete class is injected for clarity; clear() is handled by repo.clear())
        final List<LedgerMerkleFrontier> actualFrontier =
                frontierRepo.findBySubjectId(subjectId, DEFAULT_TENANT_ID);
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

        assertThat(s1.stream()
                .map(e -> ((MemoryTestEntry) e).getSequenceNumber())
                .sorted().toList())
                .containsExactly(1, 2, 3, 4);
        assertThat(s2.stream()
                .map(e -> ((MemoryTestEntry) e).getSequenceNumber())
                .sorted().toList())
                .containsExactly(1, 2, 3, 4);
    }
}

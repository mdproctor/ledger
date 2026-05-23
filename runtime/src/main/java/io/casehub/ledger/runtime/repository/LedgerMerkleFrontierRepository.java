package io.casehub.ledger.runtime.repository;

import java.util.List;
import java.util.UUID;

import io.casehub.ledger.runtime.model.LedgerMerkleFrontier;

/**
 * SPI for persisting and querying the Merkle Mountain Range frontier for a subject.
 *
 * <p>
 * The frontier is the minimal set of stored hashes that allows O(log N) inclusion proof
 * generation without re-hashing all entries. A subject with N entries has exactly
 * {@code Integer.bitCount(N)} frontier nodes at any time.
 *
 * <p>
 * The built-in JPA implementation ({@code JpaLedgerMerkleFrontierRepository}) must be
 * explicitly selected via {@code quarkus.arc.selected-alternatives} in deployments that
 * use a datasource. In-memory alternatives (e.g. from {@code casehub-ledger-memory})
 * activate automatically via CDI priority.
 */
public interface LedgerMerkleFrontierRepository {

    /**
     * Return all frontier nodes for the given subject, ordered by level ascending.
     *
     * @param subjectId the aggregate identifier
     * @return current frontier; empty if no entries have been saved for this subject
     */
    List<LedgerMerkleFrontier> findBySubjectId(UUID subjectId);

    /**
     * Atomically replace the frontier for the given subject with {@code newFrontier}.
     *
     * <p>
     * This is a full replacement: any existing nodes whose level is not present in
     * {@code newFrontier} are deleted. All nodes in {@code newFrontier} are persisted
     * (existing nodes at the same level are overwritten). The operation must be atomic
     * with the entry persist that triggered it.
     *
     * @param subjectId   the aggregate identifier
     * @param newFrontier the complete new frontier; computed by
     *                    {@link io.casehub.ledger.runtime.service.LedgerMerkleTree#append}
     */
    void replace(UUID subjectId, List<LedgerMerkleFrontier> newFrontier);
}

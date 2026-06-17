package io.casehub.ledger.runtime.repository.jpa;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;

/**
 * Atomically allocates per-subject sequence numbers from the {@code ledger_subject_sequence} table.
 *
 * <p>Uses {@code INSERT ... ON CONFLICT DO NOTHING} followed by
 * {@code UPDATE SET next_seq = next_seq + 1}. Both H2 2.2+ (in {@code MODE=PostgreSQL}) and
 * PostgreSQL support this syntax. H2's grammar accepts only the no-column-target form
 * ({@code ON CONFLICT DO NOTHING}); PostgreSQL accepts both forms. Using the no-column-target
 * form is valid for both: the {@code ledger_subject_sequence} table has exactly one unique
 * constraint (the PK on {@code (subject_id, tenancy_id)}), so the target is unambiguous.
 *
 * <p><strong>Concurrent first-insert safety:</strong> when two transactions race for a new
 * {@code (subjectId, tenancyId)} pair, exactly one INSERT creates the row; the other silently
 * does nothing (DO NOTHING suppresses the unique-constraint violation rather than propagating it).
 * Both transactions then issue the UPDATE, which serialises on the row lock held by whichever
 * transaction arrived first. The loser blocks at the UPDATE until the winner commits.
 *
 * <p><strong>Merkle Serialization Invariant:</strong> the row lock acquired by the UPDATE is held
 * for the duration of the enclosing {@code @Transactional save()} in
 * {@code JpaLedgerEntryRepository}. This serialises the entire save pipeline — including the
 * Merkle frontier update — for concurrent saves to the same {@code (subject, tenancy)} pair.
 * Saves for different {@code (subjectId, tenancyId)} pairs proceed independently.
 * See GE-20260615-6d0ae3 and the concurrent-write-safety spec (issue #100).
 *
 * <p>Shared by all JPA repositories that persist
 * {@link io.casehub.ledger.runtime.model.LedgerEntry} subclasses.
 */
@ApplicationScoped
class LedgerSequenceAllocator {

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    /**
     * Returns the next contiguous sequence number for the given {@code (subjectId, tenancyId)} pair.
     *
     * <p>The INSERT seeds the row with {@code next_seq = 1} on first use; the UPDATE increments it.
     * After the UPDATE, {@code next_seq} holds the allocated value + 1; the SELECT returns
     * {@code next_seq - 1} (the allocated sequence number). First allocation returns 1.
     *
     * <p>{@code CAST(?1 AS UUID)} is required: both H2 and PostgreSQL require an explicit cast
     * to coerce the JDBC parameter to the UUID column type.
     */
    int nextSequenceNumber(final UUID subjectId, final String tenancyId) {
        em.createNativeQuery(
                "INSERT INTO ledger_subject_sequence (subject_id, tenancy_id, next_seq) " +
                "VALUES (CAST(?1 AS UUID), ?2, 1) " +
                "ON CONFLICT DO NOTHING")
                .setParameter(1, subjectId)
                .setParameter(2, tenancyId)
                .executeUpdate();
        em.createNativeQuery(
                "UPDATE ledger_subject_sequence " +
                "SET next_seq = next_seq + 1 " +
                "WHERE subject_id = CAST(?1 AS UUID) AND tenancy_id = ?2")
                .setParameter(1, subjectId)
                .setParameter(2, tenancyId)
                .executeUpdate();
        em.flush(); // flush before SELECT reads the result within the same transaction
        final Number nextSeq = (Number) em.createNativeQuery(
                "SELECT next_seq - 1 FROM ledger_subject_sequence " +
                "WHERE subject_id = ?1 AND tenancy_id = ?2")
                .setParameter(1, subjectId)
                .setParameter(2, tenancyId)
                .getSingleResult();
        return nextSeq.intValue();
    }
}

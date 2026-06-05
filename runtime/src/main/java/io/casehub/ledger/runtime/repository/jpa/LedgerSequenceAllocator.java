package io.casehub.ledger.runtime.repository.jpa;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;

/**
 * Atomically allocates per-subject sequence numbers from the {@code ledger_subject_sequence} table.
 *
 * <p>Uses SQL-standard MERGE — works on both H2 (test) and PostgreSQL 15+ (production).
 * Shared by all JPA repositories that persist {@link io.casehub.ledger.runtime.model.LedgerEntry}
 * subclasses.
 */
@ApplicationScoped
class LedgerSequenceAllocator {

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    /**
     * Returns the next contiguous sequence number for the given subject.
     *
     * <p>{@code next_seq} stores the NEXT value to allocate. First insert sets {@code next_seq=2}
     * (allocating 1). WHEN MATCHED increments for subsequent allocations.
     */
    int nextSequenceNumber(final UUID subjectId) {
        em.createNativeQuery(
                "MERGE INTO ledger_subject_sequence AS t " +
                "USING (SELECT CAST(?1 AS UUID) AS sid) AS s " +
                "ON t.subject_id = s.sid " +
                "WHEN MATCHED THEN UPDATE SET next_seq = t.next_seq + 1 " +
                "WHEN NOT MATCHED THEN INSERT (subject_id, next_seq) VALUES (s.sid, 2)")
                .setParameter(1, subjectId)
                .executeUpdate();
        em.flush(); // force MERGE to DB before SELECT reads its result
        final Number nextSeq = (Number) em.createNativeQuery(
                "SELECT next_seq - 1 FROM ledger_subject_sequence WHERE subject_id = ?1")
                .setParameter(1, subjectId)
                .getSingleResult();
        return nextSeq.intValue();
    }
}

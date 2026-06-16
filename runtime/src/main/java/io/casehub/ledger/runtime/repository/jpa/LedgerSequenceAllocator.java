package io.casehub.ledger.runtime.repository.jpa;

import java.util.Locale;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.hibernate.Session;

import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;

/**
 * Atomically allocates per-subject sequence numbers from the {@code ledger_subject_sequence} table.
 *
 * <p><strong>H2 vs PostgreSQL:</strong> H2 2.x does not support {@code ON CONFLICT} syntax at
 * all (not even {@code DO NOTHING}). This class detects the live database product at first use
 * via {@code DatabaseMetaData.getDatabaseProductName()} and selects the appropriate SQL branch:
 * <ul>
 *   <li><b>PostgreSQL</b>: {@code INSERT ON CONFLICT DO NOTHING} + {@code UPDATE}. Race-safe for
 *   concurrent first-inserts — the second inserter blocks on the INSERT conflict until the first
 *   commits, then applies DO NOTHING; both then serialize on the UPDATE row lock.</li>
 *   <li><b>H2</b>: standard SQL MERGE. H2 uses table-level locking which prevents concurrent
 *   first-insert races; all H2 tests are serial so this is safe in practice.</li>
 * </ul>
 *
 * <p><strong>Merkle Serialization Invariant:</strong> the row lock acquired by
 * {@code nextSequenceNumber()} is held for the duration of the enclosing
 * {@code @Transactional save()} in {@code JpaLedgerEntryRepository}. This serialises the entire
 * save pipeline — including the Merkle frontier update — for concurrent saves to the same
 * {@code (subject, tenancy)} pair. Saves across different tenants with the same {@code subjectId}
 * (e.g. the nameUUID-derived subjects of {@code KeyRotationEntry}) proceed independently.
 * See the concurrent-write-safety spec (issue #100).
 *
 * <p>Shared by all JPA repositories that persist {@link io.casehub.ledger.runtime.model.LedgerEntry}
 * subclasses.
 */
@ApplicationScoped
class LedgerSequenceAllocator {

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    // Cached result of JDBC product name detection — see isPostgresql(). Fixed per application
    // lifetime; safe to cache. casehubio/ledger#141
    private volatile Boolean postgresql = null;

    /**
     * Returns the next contiguous sequence number for the given subject.
     *
     * <p>For PostgreSQL: {@code INSERT ON CONFLICT DO NOTHING} seeds the row if absent; the
     * subsequent {@code UPDATE} increments {@code next_seq} under a row lock held for the
     * transaction duration. {@code next_seq} starts at 1; after the UPDATE it is 2; the SELECT
     * returns {@code next_seq - 1 = 1} for the first allocation.
     *
     * <p>For H2: standard SQL MERGE — {@code WHEN NOT MATCHED} inserts with {@code next_seq=2}
     * (allocating 1); {@code WHEN MATCHED} increments. {@code CAST(?1 AS UUID)} is required
     * because H2 cannot coerce a raw parameter to a UUID-typed column.
     */
    int nextSequenceNumber(final UUID subjectId, final String tenancyId) {
        if (isPostgresql()) {
            postgresqlNextSequenceNumber(subjectId, tenancyId);
        } else {
            h2NextSequenceNumber(subjectId, tenancyId);
        }
        em.flush(); // flush writes before SELECT reads the result in the same transaction
        final Number nextSeq = (Number) em.createNativeQuery(
                "SELECT next_seq - 1 FROM ledger_subject_sequence WHERE subject_id = ?1 AND tenancy_id = ?2")
                .setParameter(1, subjectId)
                .setParameter(2, tenancyId)
                .getSingleResult();
        return nextSeq.intValue();
    }

    private boolean isPostgresql() {
        Boolean result = postgresql;
        if (result == null) {
            result = em.unwrap(Session.class)
                    .doReturningWork(conn -> conn.getMetaData().getDatabaseProductName()
                            .toLowerCase(Locale.ROOT).contains("postgresql"));
            postgresql = result;
        }
        return result;
    }

    private void postgresqlNextSequenceNumber(final UUID subjectId, final String tenancyId) {
        // Seed the row if the subject+tenant is new.
        // Concurrent first-insert behaviour: if two transactions race for a new (subject, tenancyId),
        // the second blocks on the INSERT conflict until the first *commits* (DO NOTHING),
        // or until the first *rolls back* (INSERT then succeeds). Either way, exactly one row
        // is created before both proceed to the UPDATE below.
        em.createNativeQuery(
                "INSERT INTO ledger_subject_sequence (subject_id, tenancy_id, next_seq) " +
                "VALUES (CAST(?1 AS UUID), ?2, 1) " +
                "ON CONFLICT (subject_id, tenancy_id) DO NOTHING")
                .setParameter(1, subjectId)
                .setParameter(2, tenancyId)
                .executeUpdate();
        // Always increment. PostgreSQL serialises concurrent UPDATE calls for the same row
        // via the row lock, which is held for the transaction duration. This lock is what
        // serialises the entire save pipeline (including Merkle frontier update) for
        // concurrent saves to the same subject — the Merkle Serialization Invariant.
        em.createNativeQuery(
                "UPDATE ledger_subject_sequence " +
                "SET next_seq = next_seq + 1 " +
                "WHERE subject_id = CAST(?1 AS UUID) AND tenancy_id = ?2")
                .setParameter(1, subjectId)
                .setParameter(2, tenancyId)
                .executeUpdate();
    }

    private void h2NextSequenceNumber(final UUID subjectId, final String tenancyId) {
        // CAST(? AS VARCHAR) on the tenancy_id parameter avoids H2 mis-parsing the alias
        // as a type name ('tid' is a PostgreSQL type that H2 doesn't recognise).
        em.createNativeQuery(
                "MERGE INTO ledger_subject_sequence AS t " +
                "USING (SELECT CAST(?1 AS UUID) AS sid, CAST(?2 AS VARCHAR) AS tval) AS s " +
                "ON t.subject_id = s.sid AND t.tenancy_id = s.tval " +
                "WHEN MATCHED THEN UPDATE SET next_seq = t.next_seq + 1 " +
                "WHEN NOT MATCHED THEN INSERT (subject_id, tenancy_id, next_seq) VALUES (s.sid, s.tval, 2)")
                .setParameter(1, subjectId)
                .setParameter(2, tenancyId)
                .executeUpdate();
    }
}

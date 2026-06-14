package io.casehub.ledger.runtime.repository.jpa;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;

/**
 * Atomically allocates per-subject sequence numbers from the {@code ledger_subject_sequence} table.
 *
 * <p><strong>H2 vs PostgreSQL:</strong> H2 2.x does not support {@code ON CONFLICT} syntax at
 * all (not even {@code DO NOTHING}). This class uses dialect-aware SQL selected at runtime via
 * {@code quarkus.datasource.db-kind}:
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
 * save pipeline — including the Merkle frontier update — for concurrent saves to the same subject.
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

    // Reads the *default* datasource db-kind. If the ledger is configured with a named
    // datasource (via `casehub.ledger.datasource`), the named datasource's db-kind is at
    // `quarkus.datasource."<name>".db-kind`, not `quarkus.datasource.db-kind` — so this
    // property would return "h2" (the default) even if the named datasource is PostgreSQL.
    // In that case, the H2 MERGE branch would run against a PostgreSQL connection, which
    // would fail at runtime with a SQL syntax error (not silent corruption). Tracked in
    // casehubio/ledger#140. For the standard single-datasource deployment, this is correct.
    @ConfigProperty(name = "quarkus.datasource.db-kind", defaultValue = "h2")
    String dbKind;

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
    int nextSequenceNumber(final UUID subjectId) {
        if ("postgresql".equals(dbKind)) {
            postgresqlNextSequenceNumber(subjectId);
        } else {
            h2NextSequenceNumber(subjectId);
        }
        em.flush(); // flush writes before SELECT reads the result in the same transaction
        final Number nextSeq = (Number) em.createNativeQuery(
                "SELECT next_seq - 1 FROM ledger_subject_sequence WHERE subject_id = ?1")
                .setParameter(1, subjectId)
                .getSingleResult();
        return nextSeq.intValue();
    }

    private void postgresqlNextSequenceNumber(final UUID subjectId) {
        // Seed the row if the subject is new.
        // Concurrent first-insert behaviour: if two transactions race for a new subject,
        // the second blocks on the INSERT conflict until the first *commits* (DO NOTHING),
        // or until the first *rolls back* (INSERT then succeeds — the row is newly seeded by
        // the surviving transaction). Either way, exactly one row is created before both
        // proceed to the UPDATE below.
        em.createNativeQuery(
                "INSERT INTO ledger_subject_sequence (subject_id, next_seq) " +
                "VALUES (CAST(?1 AS UUID), 1) " +
                "ON CONFLICT (subject_id) DO NOTHING")
                .setParameter(1, subjectId)
                .executeUpdate();
        // Always increment. PostgreSQL serialises concurrent UPDATE calls for the same row
        // via the row lock, which is held for the transaction duration. This lock is what
        // serialises the entire save pipeline (including Merkle frontier update) for
        // concurrent saves to the same subject — the Merkle Serialization Invariant.
        em.createNativeQuery(
                "UPDATE ledger_subject_sequence " +
                "SET next_seq = next_seq + 1 " +
                "WHERE subject_id = CAST(?1 AS UUID)")
                .setParameter(1, subjectId)
                .executeUpdate();
    }

    private void h2NextSequenceNumber(final UUID subjectId) {
        em.createNativeQuery(
                "MERGE INTO ledger_subject_sequence AS t " +
                "USING (SELECT CAST(?1 AS UUID) AS sid) AS s " +
                "ON t.subject_id = s.sid " +
                "WHEN MATCHED THEN UPDATE SET next_seq = t.next_seq + 1 " +
                "WHEN NOT MATCHED THEN INSERT (subject_id, next_seq) VALUES (s.sid, 2)")
                .setParameter(1, subjectId)
                .executeUpdate();
    }
}

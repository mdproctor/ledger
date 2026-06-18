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
 * <p><strong>Dialect selection:</strong> detected once at first use via JDBC metadata and cached
 * for the application lifetime. Three paths:
 *
 * <ul>
 *   <li><b>PostgreSQL</b>: single-statement upsert —
 *   {@code INSERT … ON CONFLICT (subject_id, tenancy_id) DO UPDATE SET next_seq = next_seq + 1}.
 *   Seeds the row on first use and increments on all subsequent calls atomically. The
 *   {@code DO UPDATE} row lock is held for the duration of the enclosing
 *   {@code @Transactional save()}, which is the Merkle Serialization Invariant: saves to
 *   the same {@code (subject, tenancy)} pair are fully serialised including the Merkle
 *   frontier update. Refs casehubio/ledger#151.</li>
 *   <li><b>H2 in {@code MODE=PostgreSQL}</b>: {@code INSERT ON CONFLICT DO NOTHING} +
 *   {@code UPDATE}. H2 2.x accepts the no-column-target {@code ON CONFLICT DO NOTHING} form
 *   but rejects {@code ON CONFLICT (col, col) DO UPDATE}, so two statements are required.
 *   The {@code UPDATE} row lock preserves the Merkle Serialization Invariant.</li>
 *   <li><b>H2 in standard mode</b>: SQL-standard
 *   {@code MERGE INTO … WHEN MATCHED THEN UPDATE WHEN NOT MATCHED THEN INSERT} — a
 *   single-statement upsert. H2 in standard mode does not accept {@code ON CONFLICT} syntax.
 *   This path is used by downstream modules that run tests with a plain H2 datasource.
 *   Concurrent first-inserts are not safe here — two transactions can race for the same new
 *   row; H2 is used only in single-threaded test contexts, not production. Production always
 *   uses PostgreSQL.</li>
 * </ul>
 *
 * <p>Detection queries {@code INFORMATION_SCHEMA.SETTINGS} on H2 to check whether
 * {@code MODE=PostgreSQL} is active — both H2 modes report {@code "H2"} as the product name,
 * so {@code getDatabaseProductName()} alone is insufficient. Refs casehubio/ledger#150.
 *
 * <p>Shared by all JPA repositories that persist
 * {@link io.casehub.ledger.runtime.model.LedgerEntry} subclasses.
 */
@ApplicationScoped
class LedgerSequenceAllocator {

    private enum Dialect { POSTGRESQL, H2_PG_MODE, H2_STANDARD }

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    // Cached result of dialect detection — fixed per application lifetime.
    private volatile Dialect dialect = null;

    /**
     * Returns the next contiguous sequence number for the given {@code (subjectId, tenancyId)} pair.
     *
     * <p>First allocation for a pair returns 1. Subsequent calls increment monotonically.
     * {@code next_seq} stores the next value to allocate (post-increment); upserts seed it at
     * 2 on first use so the returned value ({@code next_seq - 1}) is 1. {@code CAST(?1 AS UUID)}
     * is required for both H2 and PostgreSQL to coerce the JDBC parameter to the UUID column type.
     */
    int nextSequenceNumber(final UUID subjectId, final String tenancyId) {
        final Dialect d = resolvedDialect();
        if (d == Dialect.POSTGRESQL) {
            onConflictUpsert(subjectId, tenancyId);
        } else if (d == Dialect.H2_PG_MODE) {
            onConflictSeed(subjectId, tenancyId);
            incrementSequence(subjectId, tenancyId);
        } else {
            mergeUpsert(subjectId, tenancyId);
        }
        em.flush();
        final Number nextSeq = (Number) em.createNativeQuery(
                "SELECT next_seq - 1 FROM ledger_subject_sequence " +
                "WHERE subject_id = ?1 AND tenancy_id = ?2")
                .setParameter(1, subjectId)
                .setParameter(2, tenancyId)
                .getSingleResult();
        return nextSeq.intValue();
    }

    private void onConflictUpsert(final UUID subjectId, final String tenancyId) {
        // Single atomic upsert for PostgreSQL: seeds next_seq=2 on first use (allocating seq=1)
        // and increments on all subsequent calls. DO UPDATE acquires an exclusive row lock held
        // for the transaction duration — this is the Merkle Serialization Invariant.
        em.createNativeQuery(
                        "INSERT INTO ledger_subject_sequence (subject_id, tenancy_id, next_seq) " +
                        "VALUES (CAST(?1 AS UUID), ?2, 2) " +
                        "ON CONFLICT (subject_id, tenancy_id) DO UPDATE " +
                        "SET next_seq = ledger_subject_sequence.next_seq + 1")
                .setParameter(1, subjectId)
                .setParameter(2, tenancyId)
                .executeUpdate();
    }

    private void onConflictSeed(final UUID subjectId, final String tenancyId) {
        // H2 MODE=PostgreSQL: seeds the row on first use. DO NOTHING handles the expected
        // conflict when a concurrent transaction created the row first. H2 2.x supports the
        // no-column-target form but not ON CONFLICT (col, col) DO UPDATE.
        em.createNativeQuery(
                        "INSERT INTO ledger_subject_sequence (subject_id, tenancy_id, next_seq) " +
                        "VALUES (CAST(?1 AS UUID), ?2, 1) " +
                        "ON CONFLICT DO NOTHING")
                .setParameter(1, subjectId)
                .setParameter(2, tenancyId)
                .executeUpdate();
    }

    private void incrementSequence(final UUID subjectId, final String tenancyId) {
        // H2 MODE=PostgreSQL: always increments. The row lock is held for the transaction
        // duration — this is the Merkle Serialization Invariant for the H2 test path.
        em.createNativeQuery(
                        "UPDATE ledger_subject_sequence " +
                        "SET next_seq = next_seq + 1 " +
                        "WHERE subject_id = CAST(?1 AS UUID) AND tenancy_id = ?2")
                .setParameter(1, subjectId)
                .setParameter(2, tenancyId)
                .executeUpdate();
    }

    private void mergeUpsert(final UUID subjectId, final String tenancyId) {
        // H2 standard mode: single-statement upsert. CAST(?2 AS VARCHAR) avoids H2
        // misinterpreting the alias as a type name. Not concurrent-safe for first inserts
        // (H2 MVStore MERGE does not apply index-level locking); single-threaded tests only.
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

    private Dialect resolvedDialect() {
        Dialect d = dialect;
        if (d == null) {
            d = em.unwrap(Session.class).doReturningWork(conn -> {
                final String productName = conn.getMetaData().getDatabaseProductName()
                        .toLowerCase(Locale.ROOT);
                if (productName.contains("postgresql")) {
                    return Dialect.POSTGRESQL;
                }
                if (productName.contains("h2")) {
                    try (var stmt = conn.createStatement();
                         var rs = stmt.executeQuery(
                                 "SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS " +
                                 "WHERE SETTING_NAME = 'MODE'")) {
                        if (rs.next() && "PostgreSQL".equalsIgnoreCase(rs.getString(1))) {
                            return Dialect.H2_PG_MODE;
                        }
                    } catch (final Exception ignored) {
                        // older H2 or INFORMATION_SCHEMA unavailable — fall through
                    }
                }
                return Dialect.H2_STANDARD;
            });
            dialect = d;
        }
        return d;
    }
}

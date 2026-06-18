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
 * for the application lifetime.
 *
 * <ul>
 *   <li><b>PostgreSQL and H2 in {@code MODE=PostgreSQL}</b>: {@code INSERT ON CONFLICT DO NOTHING}
 *   + {@code UPDATE}. The INSERT seeds the row on first use; concurrent first-inserts are safe —
 *   one wins, the other silently does nothing, both then serialise on the UPDATE row lock. The row
 *   lock is held for the enclosing {@code @Transactional save()}, which is the Merkle
 *   Serialization Invariant: saves to the same {@code (subject, tenancy)} pair are fully
 *   serialised including the Merkle frontier update.</li>
 *   <li><b>H2 in standard mode</b>: SQL-standard {@code MERGE INTO … WHEN NOT MATCHED THEN INSERT}
 *   + {@code UPDATE}. H2 in standard mode does not accept {@code ON CONFLICT} syntax. This path
 *   is used by downstream modules (e.g. {@code casehub-engine-ledger}) that run tests with a
 *   plain H2 datasource. Concurrent first-inserts are not safe here — two transactions can race
 *   for the same new row; but H2 is used only in single-threaded test contexts, not production.
 *   Production deployments always use PostgreSQL.</li>
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

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    // Cached result of dialect detection — fixed per application lifetime.
    private volatile Boolean useOnConflict = null;

    /**
     * Returns the next contiguous sequence number for the given {@code (subjectId, tenancyId)} pair.
     *
     * <p>First allocation for a pair returns 1. Subsequent calls increment monotonically.
     * {@code CAST(?1 AS UUID)} is required for both H2 and PostgreSQL to coerce the JDBC
     * parameter to the UUID column type.
     */
    int nextSequenceNumber(final UUID subjectId, final String tenancyId) {
        if (useOnConflictSyntax()) {
            onConflictInsert(subjectId, tenancyId);
        } else {
            mergeInsert(subjectId, tenancyId);
        }
        em.createNativeQuery(
                "UPDATE ledger_subject_sequence " +
                "SET next_seq = next_seq + 1 " +
                "WHERE subject_id = CAST(?1 AS UUID) AND tenancy_id = ?2")
                .setParameter(1, subjectId)
                .setParameter(2, tenancyId)
                .executeUpdate();
        em.flush();
        final Number nextSeq = (Number) em.createNativeQuery(
                "SELECT next_seq - 1 FROM ledger_subject_sequence " +
                "WHERE subject_id = ?1 AND tenancy_id = ?2")
                .setParameter(1, subjectId)
                .setParameter(2, tenancyId)
                .getSingleResult();
        return nextSeq.intValue();
    }

    private void onConflictInsert(final UUID subjectId, final String tenancyId) {
        em.createNativeQuery(
                "INSERT INTO ledger_subject_sequence (subject_id, tenancy_id, next_seq) " +
                "VALUES (CAST(?1 AS UUID), ?2, 1) " +
                "ON CONFLICT DO NOTHING")
                .setParameter(1, subjectId)
                .setParameter(2, tenancyId)
                .executeUpdate();
    }

    private void mergeInsert(final UUID subjectId, final String tenancyId) {
        // SQL-standard MERGE: inserts the row only when no match exists.
        // CAST(?2 AS VARCHAR) avoids H2 misinterpreting the alias as a type name.
        em.createNativeQuery(
                "MERGE INTO ledger_subject_sequence AS t " +
                "USING (SELECT CAST(?1 AS UUID) AS sid, CAST(?2 AS VARCHAR) AS tval) AS s " +
                "ON t.subject_id = s.sid AND t.tenancy_id = s.tval " +
                "WHEN NOT MATCHED THEN INSERT (subject_id, tenancy_id, next_seq) VALUES (s.sid, s.tval, 1)")
                .setParameter(1, subjectId)
                .setParameter(2, tenancyId)
                .executeUpdate();
    }

    private boolean useOnConflictSyntax() {
        Boolean result = useOnConflict;
        if (result == null) {
            result = em.unwrap(Session.class).doReturningWork(conn -> {
                final String productName = conn.getMetaData().getDatabaseProductName()
                        .toLowerCase(Locale.ROOT);
                if (productName.contains("postgresql")) {
                    return true; // real PostgreSQL
                }
                if (!productName.contains("h2")) {
                    return false; // unknown DB — safe default: use MERGE
                }
                // H2: ON CONFLICT is only supported when MODE=PostgreSQL is active.
                // getURL() may omit connection properties, so query INFORMATION_SCHEMA directly.
                try (var stmt = conn.createStatement();
                     var rs = stmt.executeQuery(
                         "SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS " +
                         "WHERE SETTING_NAME = 'MODE'")) {
                    return rs.next() && "PostgreSQL".equalsIgnoreCase(rs.getString(1));
                } catch (final Exception ignored) {
                    return false; // older H2 or schema unavailable — use MERGE
                }
            });
            useOnConflict = result;
        }
        return result;
    }
}

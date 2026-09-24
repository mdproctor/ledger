package io.casehub.ledger.jpa;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;

import java.util.Locale;
import java.util.UUID;

public class LedgerSequenceAllocator {

    private enum Dialect {POSTGRESQL, H2_PG_MODE, H2_STANDARD}

    private final EntityManager em;

    private volatile Dialect dialect = null;

    public LedgerSequenceAllocator(EntityManager em) {
        this.em = em;
    }

    public int nextSequenceNumber(final UUID subjectId, final String tenancyId) {
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
        em.createNativeQuery(
                  "INSERT INTO ledger_subject_sequence (subject_id, tenancy_id, next_seq) " +
                  "VALUES (CAST(?1 AS UUID), ?2, 1) " +
                  "ON CONFLICT DO NOTHING")
          .setParameter(1, subjectId)
          .setParameter(2, tenancyId)
          .executeUpdate();
    }

    private void incrementSequence(final UUID subjectId, final String tenancyId) {
        em.createNativeQuery(
                  "UPDATE ledger_subject_sequence " +
                  "SET next_seq = next_seq + 1 " +
                  "WHERE subject_id = CAST(?1 AS UUID) AND tenancy_id = ?2")
          .setParameter(1, subjectId)
          .setParameter(2, tenancyId)
          .executeUpdate();
    }

    private void mergeUpsert(final UUID subjectId, final String tenancyId) {
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
            d       = em.unwrap(Session.class).doReturningWork(conn -> {
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
                    }
                }
                return Dialect.H2_STANDARD;
            });
            dialect = d;
        }
        return d;
    }
}

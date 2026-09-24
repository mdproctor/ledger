package io.casehub.ledger.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A peer attestation stamped onto a {@link LedgerEntry}.
 * Plain {@code @Entity} — queries via {@code @NamedQuery} + EntityManager.
 */
@Entity
@Table(name = "ledger_attestation")
@NamedQuery(
        name = "LedgerAttestation.findByEntryId",
        query = "SELECT a FROM LedgerAttestation a WHERE a.ledgerEntryId = :entryId ORDER BY a.occurredAt ASC")
@NamedQuery(
        name = "LedgerAttestation.findBySubjectId",
        query = "SELECT a FROM LedgerAttestation a WHERE a.subjectId = :subjectId ORDER BY a.occurredAt ASC")
@NamedQuery(
        name = "LedgerAttestation.findByEntryIds",
        query = "SELECT a FROM LedgerAttestation a WHERE a.ledgerEntryId IN :entryIds")
@NamedQuery(
        name = "LedgerAttestation.findByEntryIdAndCapabilityTag",
        query = "SELECT a FROM LedgerAttestation a WHERE a.ledgerEntryId = :entryId AND a.capabilityTag = :capabilityTag ORDER BY a.occurredAt ASC")
// '*' is CapabilityTag.GLOBAL — JPQL cannot reference Java constants directly
@NamedQuery(
        name = "LedgerAttestation.findGlobalByEntryId",
        query = "SELECT a FROM LedgerAttestation a WHERE a.ledgerEntryId = :entryId AND a.capabilityTag = '*' ORDER BY a.occurredAt ASC")
@NamedQuery(
        name = "LedgerAttestation.findByAttestorIdAndCapabilityTag",
        query = "SELECT a FROM LedgerAttestation a WHERE a.attestorId = :attestorId AND a.capabilityTag = :capabilityTag ORDER BY a.occurredAt ASC")
@NamedQuery(
        name = "LedgerAttestation.findByActorIdEvents",
        query = "SELECT a FROM LedgerAttestation a WHERE a.ledgerEntryId IN ("
              + "SELECT e.id FROM LedgerEntry e WHERE e.actorId = :actorId AND e.entryType = :type)")
@NamedQuery(
        name = "LedgerAttestation.findByEntryIdAndTenancyId",
        query = "SELECT a FROM LedgerAttestation a JOIN LedgerEntry e ON a.ledgerEntryId = e.id "
              + "WHERE a.ledgerEntryId = :entryId AND e.tenancyId = :tenancyId ORDER BY a.occurredAt ASC")
@NamedQuery(
        name = "LedgerAttestation.findByEntryIdAndCapabilityTagAndTenancyId",
        query = "SELECT a FROM LedgerAttestation a JOIN LedgerEntry e ON a.ledgerEntryId = e.id "
              + "WHERE a.ledgerEntryId = :entryId AND a.capabilityTag = :capabilityTag AND e.tenancyId = :tenancyId ORDER BY a.occurredAt ASC")
@NamedQuery(
        name = "LedgerAttestation.findGlobalByEntryIdAndTenancyId",
        query = "SELECT a FROM LedgerAttestation a JOIN LedgerEntry e ON a.ledgerEntryId = e.id "
              + "WHERE a.ledgerEntryId = :entryId AND a.capabilityTag = '*' AND e.tenancyId = :tenancyId ORDER BY a.occurredAt ASC")
@NamedQuery(
        name = "LedgerAttestation.findByAttestorIdAndCapabilityTagAndTenancyId",
        query = "SELECT a FROM LedgerAttestation a JOIN LedgerEntry e ON a.ledgerEntryId = e.id "
              + "WHERE a.attestorId = :attestorId AND a.capabilityTag = :capabilityTag AND e.tenancyId = :tenancyId ORDER BY a.occurredAt ASC")
@NamedQuery(
        name = "LedgerAttestation.countByActorAndVerdict",
        query = "SELECT a.verdict, COUNT(a) FROM LedgerAttestation a JOIN LedgerEntry e ON a.ledgerEntryId = e.id"
              + " WHERE e.actorId = :actorId AND e.occurredAt >= :from AND e.occurredAt <= :to"
              + " AND e.tenancyId = :tenancyId GROUP BY a.verdict")
@NamedQuery(
        name = "LedgerAttestation.countBySubjectAndVerdict",
        query = "SELECT a.verdict, COUNT(a) FROM LedgerAttestation a JOIN LedgerEntry e ON a.ledgerEntryId = e.id"
              + " WHERE e.subjectId = :subjectId AND e.occurredAt >= :from AND e.occurredAt <= :to"
              + " AND e.tenancyId = :tenancyId GROUP BY a.verdict")
@NamedQuery(
        name = "LedgerAttestation.summariseByActor",
        query = "SELECT a.verdict, COUNT(a), AVG(a.confidence), MIN(a.confidence), MAX(a.confidence)"
              + " FROM LedgerAttestation a JOIN LedgerEntry e ON a.ledgerEntryId = e.id"
              + " WHERE e.actorId = :actorId AND e.occurredAt >= :from AND e.occurredAt <= :to"
              + " AND e.tenancyId = :tenancyId GROUP BY a.verdict")
public class LedgerAttestation extends io.casehub.ledger.api.model.LedgerAttestation {


    @PrePersist
    void prePersist() {
        if (id == null)
            id = UUID.randomUUID();
        if (occurredAt == null)
            occurredAt = Instant.now();
    }
}

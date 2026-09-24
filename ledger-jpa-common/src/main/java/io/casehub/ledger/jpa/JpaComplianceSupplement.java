package io.casehub.ledger.jpa;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

import io.casehub.ledger.api.model.supplement.ComplianceSupplement;

/**
 * JPA entity for compliance supplements.
 *
 * <p>
 * Extends the api-tier {@link ComplianceSupplement} (which carries all field definitions
 * as {@code @MappedSuperclass}) and adds the JPA entity mapping, table name, and the
 * concrete {@code @ManyToOne} relationship to {@link JpaLedgerEntry}.
 *
 * <p>
 * Table: {@code ledger_supplement_compliance} — self-contained (no JOINED inheritance
 * base table). Carries its own {@code id}, {@code ledger_entry_id}, {@code supplement_type},
 * plus all compliance-specific columns.
 */
@Entity
@Table(name = "ledger_supplement_compliance")
@NamedQuery(
        name = "JpaComplianceSupplement.findByEntryIds",
        query = "SELECT cs FROM JpaComplianceSupplement cs WHERE cs.jpaLedgerEntry.id IN :ids")
public class JpaComplianceSupplement extends ComplianceSupplement {

    /**
     * JPA relationship to the owning ledger entry.
     * Overrides the {@code @Transient} back-reference in the api superclass.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ledger_entry_id", nullable = false)
    public JpaLedgerEntry jpaLedgerEntry;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (supplementType == null) {
            supplementType = "COMPLIANCE";
        }
    }

    /**
     * Sets the JPA relationship to the owning entry.
     */
    public void setLedgerEntry(final JpaLedgerEntry entry) {
        this.jpaLedgerEntry = entry;
    }
}

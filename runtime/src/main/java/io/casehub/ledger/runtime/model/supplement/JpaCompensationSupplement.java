package io.casehub.ledger.runtime.model.supplement;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

import io.casehub.ledger.api.model.supplement.CompensationSupplement;
import io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry;

/**
 * JPA entity for compensation supplements.
 *
 * <p>
 * Extends the api-tier {@link CompensationSupplement} (which carries all field definitions
 * as {@code @MappedSuperclass}) and adds the JPA entity mapping, table name, and the
 * concrete {@code @ManyToOne} relationship to {@link JpaLedgerEntry}.
 *
 * <p>
 * Table: {@code ledger_supplement_compensation} — self-contained (no JOINED inheritance
 * base table). Carries its own {@code id}, {@code ledger_entry_id}, {@code supplement_type},
 * plus all compensation-specific columns.
 */
@Entity
@Table(name = "ledger_supplement_compensation")
@NamedQuery(
        name = "JpaCompensationSupplement.findByEntryIds",
        query = "SELECT cs FROM JpaCompensationSupplement cs WHERE cs.jpaLedgerEntry.id IN :ids")
public class JpaCompensationSupplement extends CompensationSupplement {

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
            supplementType = "COMPENSATION";
        }
    }

    /**
     * Sets the JPA relationship to the owning entry.
     */
    public void setLedgerEntry(final JpaLedgerEntry entry) {
        this.jpaLedgerEntry = entry;
    }
}

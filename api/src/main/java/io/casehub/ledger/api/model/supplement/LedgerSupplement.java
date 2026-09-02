package io.casehub.ledger.api.model.supplement;

import java.util.UUID;


/**
 * Abstract base for all ledger supplements.
 *
 * <p>
 * A <strong>supplement</strong> is an optional, lazily-loaded extension to a
 * {@code LedgerEntry} that carries a named group of cross-cutting fields. Supplements
 * exist in separate tables and are never written unless the consumer explicitly
 * attaches one — consumers that do not use supplements incur zero schema or runtime cost.
 *
 * <p>
 * Three built-in supplements are provided:
 * <ul>
 * <li>{@link ComplianceSupplement} — GDPR Art.22 decision snapshot, EU AI Act Art.12,
 * governance reference, rationale</li>
 * <li>{@link ProvenanceSupplement} — workflow source entity</li>
 * <li>{@link CompensationSupplement} — saga compensation record linking to original entry</li>
 * </ul>
 *
 * <p>
 * Supplements are accessed via the typed helper methods on {@code LedgerEntry}:
 * {@code entry.compliance()} and {@code entry.provenance()}.
 * Use {@code entry.attach(supplement)} to add or replace a supplement; this also
 * keeps {@code entry.supplementJson} in sync automatically.
 *
 * <p>
 * <strong>Zero-complexity guarantee:</strong> If a consumer never calls
 * {@code entry.attach()}, no supplement table rows are written and the lazy
 * {@code supplements} list is never initialised.
 *
 * <p>
 * This class is {@code @MappedSuperclass} — it defines the common column mappings
 * inherited by all JPA supplement entities. The {@code ledgerEntry} back-reference
 * is {@code @Transient} at this level; JPA subclasses add the concrete
 * {@code @ManyToOne} relationship.
 */
public abstract class LedgerSupplement {

    /** Primary key — UUID assigned on first persist. */
    public UUID id;

    // Note: the ledgerEntry back-reference is NOT declared here. Hibernate bytecode
    // enhancement of @MappedSuperclass strips non-persistent fields. The JPA relationship
    // lives on the concrete Jpa* entity subclasses (jpaLedgerEntry field). The api-tier
    // LedgerEntry.attach() no longer needs a back-reference — it was never read.

    /**
     * Discriminator value — identifies the supplement type.
     * Use {@code instanceof} checks or {@link LedgerEntry#compliance()} etc.
     * for typed access rather than reading this field directly.
     */
    public String supplementType;
}

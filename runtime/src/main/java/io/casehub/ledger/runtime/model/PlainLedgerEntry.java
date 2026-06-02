package io.casehub.ledger.runtime.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Concrete {@link LedgerEntry} subclass for domain-agnostic event writes.
 *
 * <p>Used by {@link io.casehub.ledger.runtime.service.OutcomeRecordSaveService} to persist
 * generic EVENT entries via {@link io.casehub.ledger.api.spi.OutcomeRecorder}. Has no
 * domain-specific fields — all payload lives in {@code ledger_entry} and
 * {@code ledger_attestation}.
 *
 * <p>In-memory deployments ({@code casehub-ledger-memory}) do not use Hibernate; this class
 * works there as a plain Java object with no schema requirement.
 */
@Entity
@Table(name = "plain_ledger_entry")
@DiscriminatorValue("PLAIN")
public class PlainLedgerEntry extends LedgerEntry {
}

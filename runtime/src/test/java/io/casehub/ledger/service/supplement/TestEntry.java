package io.casehub.ledger.service.supplement;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import io.casehub.ledger.jpa.JpaLedgerEntry;

/**
 * Minimal concrete subclass of {@link JpaLedgerEntry} for integration tests only.
 * Never used in production code.
 */
@Entity
@Table(name = "test_ledger_entry")
@DiscriminatorValue("TEST")
public class TestEntry extends JpaLedgerEntry {
}

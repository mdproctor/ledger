package io.casehub.ledger.core.trust;

public interface LedgerReconciliationSource {

    String subjectType();

    long countDomainEntities();

    long countLedgerEntries();

    boolean isActive();
}

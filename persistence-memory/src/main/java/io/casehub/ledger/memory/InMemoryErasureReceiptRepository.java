package io.casehub.ledger.memory;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import io.casehub.ledger.runtime.model.ErasureReceiptLedgerEntry;
import io.casehub.ledger.runtime.repository.ErasureReceiptRepository;

@Alternative
@Priority(1)
@ApplicationScoped
public class InMemoryErasureReceiptRepository implements ErasureReceiptRepository {

    @Inject
    InMemoryLedgerEntryRepository blocking;

    @Override
    public List<ErasureReceiptLedgerEntry> findByErasedActorId(
            final String erasedActorId, final String tenancyId) {
        return blocking.allEntries().stream()
                .filter(e -> e instanceof ErasureReceiptLedgerEntry)
                .map(e -> (ErasureReceiptLedgerEntry) e)
                .filter(e -> erasedActorId.equals(e.erasedActorId))
                .filter(e -> tenancyId.equals(e.tenancyId))
                .sorted(Comparator.comparing(e -> e.occurredAt))
                .collect(Collectors.toList());
    }
}

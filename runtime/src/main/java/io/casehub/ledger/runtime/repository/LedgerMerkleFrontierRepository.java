package io.casehub.ledger.runtime.repository;

import java.util.List;
import java.util.UUID;

import io.casehub.ledger.runtime.model.LedgerMerkleFrontier;

public interface LedgerMerkleFrontierRepository {

    List<LedgerMerkleFrontier> findBySubjectId(UUID subjectId);

    void replace(UUID subjectId, List<LedgerMerkleFrontier> newFrontier);
}

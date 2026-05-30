package io.casehub.ledger.runtime.repository;

import io.casehub.ledger.runtime.model.ActorIdentityBindingEntry;
import java.util.List;
import java.util.Optional;

public interface ActorIdentityBindingRepository {
    Optional<ActorIdentityBindingEntry> latestBindingFor(String actorId);
    List<ActorIdentityBindingEntry> bindingHistoryFor(String actorId);
    ActorIdentityBindingEntry save(ActorIdentityBindingEntry entry);
}

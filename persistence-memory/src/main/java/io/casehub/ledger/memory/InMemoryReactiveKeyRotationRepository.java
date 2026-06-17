package io.casehub.ledger.memory;

import java.util.List;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import io.casehub.ledger.runtime.model.KeyRotationEntry;
import io.casehub.ledger.runtime.repository.ReactiveKeyRotationRepository;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;

@Alternative
@Priority(1)
@ApplicationScoped
@IfBuildProperty(name = "casehub.ledger.reactive.enabled", stringValue = "true")
public class InMemoryReactiveKeyRotationRepository implements ReactiveKeyRotationRepository {

    @Inject
    InMemoryKeyRotationRepository blocking;

    @Override
    public Uni<List<KeyRotationEntry>> findByActorId(final String actorId, final String tenancyId) {
        return Uni.createFrom().item(() -> blocking.findByActorId(actorId, tenancyId));
    }

    @Override
    public Uni<List<KeyRotationEntry>> findCompromisedByActorIdAndKeyRef(
            final String actorId, final String keyRef) {
        return Uni.createFrom().item(() -> blocking.findCompromisedByActorIdAndKeyRef(actorId, keyRef));
    }
}

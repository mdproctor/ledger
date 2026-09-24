package io.casehub.ledger.runtime.service.federation;

import io.casehub.ledger.core.federation.TrustBootstrapSource;
import io.casehub.ledger.core.federation.TrustImportService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

@ApplicationScoped
public class TrustBootstrapService {

    private final io.casehub.ledger.core.federation.TrustBootstrapServiceCore core;

    @Inject
    TrustBootstrapService(TrustBootstrapSource bootstrapSource, TrustImportService importService) {
        this.core = new io.casehub.ledger.core.federation.TrustBootstrapServiceCore(bootstrapSource, importService);
    }

    public void bootstrapIfNew(Set<String> newActorIds) {
        core.bootstrapIfNew(newActorIds);
    }
}

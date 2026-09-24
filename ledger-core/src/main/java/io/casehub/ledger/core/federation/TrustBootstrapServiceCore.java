package io.casehub.ledger.core.federation;

import java.util.Set;

public class TrustBootstrapServiceCore {

    private final TrustBootstrapSource bootstrapSource;
    private final TrustImportService importService;

    public TrustBootstrapServiceCore(TrustBootstrapSource bootstrapSource,
                                     TrustImportService importService) {
        this.bootstrapSource = bootstrapSource;
        this.importService = importService;
    }

    public void bootstrapIfNew(Set<String> newActorIds) {
        for (String actorId : newActorIds) {
            bootstrapSource.fetchPriorTrust(actorId)
                    .ifPresent(importService::importTrust);
        }
    }
}

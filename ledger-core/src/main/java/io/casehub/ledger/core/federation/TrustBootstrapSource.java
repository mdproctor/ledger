package io.casehub.ledger.core.federation;

import java.util.Optional;

public interface TrustBootstrapSource {

    Optional<TrustExportPayload> fetchPriorTrust(String actorId);
}

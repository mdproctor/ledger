package io.casehub.ledger.runtime.service.federation;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

/** Default no-op — trust import is opt-in. Activate {@link JpaTrustImportService} or provide a custom implementation. */
@DefaultBean
@ApplicationScoped
public class NoOpTrustImportService implements TrustImportService {

    @Override
    public void importTrust(final TrustExportPayload payload) {
        // no-op
    }
}

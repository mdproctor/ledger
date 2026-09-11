package io.casehub.ledger.core.federation;

/** Default no-op — trust import is opt-in. */
public class NoOpTrustImportService implements TrustImportService {

    @Override
    public void importTrust(final TrustExportPayload payload) {
        // no-op
    }
}

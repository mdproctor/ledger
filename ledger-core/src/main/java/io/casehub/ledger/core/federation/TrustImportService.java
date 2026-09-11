package io.casehub.ledger.core.federation;

public interface TrustImportService {

    void importTrust(TrustExportPayload payload);
}

package io.casehub.ledger.runtime.service.federation;

/**
 * SPI for importing trust scores from an external {@link TrustExportPayload}.
 *
 * <p>
 * The implementation is the strategy — different implementations embody different merge
 * behaviours (seed-if-absent, weighted average, replace). Consumers provide a custom
 * implementation as a CDI {@code @Alternative} when the built-in
 * {@link JpaTrustImportService} does not suit their merge policy.
 *
 * <p>
 * Default: {@link NoOpTrustImportService} — no scores are written.
 * Built-in alternative: {@link JpaTrustImportService} — seed-if-absent for all score types.
 */
public interface TrustImportService {

    /**
     * Import trust scores from the given payload into this deployment.
     * Implementations decide how to handle conflicts with existing scores.
     *
     * @param payload the trust scores to import
     */
    void importTrust(TrustExportPayload payload);
}

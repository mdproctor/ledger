package io.casehub.ledger.runtime.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;

import io.casehub.ledger.runtime.config.LedgerConfig;

/**
 * Logs a WARNING at startup if EigenTrust is enabled with an insufficient pre-trusted actor set.
 *
 * <p>EigenTrust requires a well-connected attestation graph with at least 3 pre-trusted actors
 * to converge correctly. A star graph (one attestor, N decision-makers) with fewer than 3
 * pre-trusted actors produces degenerate scores or non-convergent iteration. See ADR 0016.
 *
 * <p>{@link #shouldWarn} is package-private for direct unit testing.
 */
@ApplicationScoped
public class EigenTrustStartupValidator {

    private static final Logger LOG = Logger.getLogger(EigenTrustStartupValidator.class);

    @Inject
    LedgerConfig config;

    void onStart(@Observes final StartupEvent ev) {
        final boolean eigenEnabled = config.trustScore().eigentrust().enabled();
        final List<String> actors = config.trustScore().eigentrust().preTrustedActors()
                .orElse(List.of());
        if (shouldWarn(eigenEnabled, actors.size())) {
            LOG.warn("casehub-ledger: EigenTrust is enabled but pre-trusted-actors has fewer "
                    + "than 3 entries. EigenTrust is inappropriate for small agent graphs or "
                    + "single-attestor deployments — results may be degenerate or non-convergent. "
                    + "Disable with: casehub.ledger.trust-score.eigentrust.enabled=false "
                    + "(the default). See ADR 0016.");
        }
    }

    /** Package-private for unit testing without CDI. */
    static boolean shouldWarn(final boolean eigenTrustEnabled, final int preTrustedActorCount) {
        return eigenTrustEnabled && preTrustedActorCount < 3;
    }
}

package io.casehub.ledger.service.federation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.core.federation.NoOpTrustBootstrapSource;

class TrustBootstrapSourceTest {

    @Test
    void noOp_alwaysReturnsEmpty() {
        final var source = new NoOpTrustBootstrapSource();
        assertThat(source.fetchPriorTrust("claude:analyst@v1")).isEmpty();
        assertThat(source.fetchPriorTrust("user-123")).isEmpty();
        assertThat(source.fetchPriorTrust("")).isEmpty();
    }
}

package io.casehub.ledger.core.service.identity;

import io.casehub.ledger.core.service.TraceIdEnricherCore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnricherPriorityOrderTest {

    @Test
    void traceIdBeforeDID() {
        assertThat(new TraceIdEnricherCore(null).priority())
                .isLessThan(new ActorDIDEnricherCore(null).priority());
    }

    @Test
    void didResolutionBeforeValidation() {
        assertThat(new ActorDIDEnricherCore(null).priority())
                .isLessThan(new IdentityValidationEnricherCore(null, null, null).priority());
    }
}

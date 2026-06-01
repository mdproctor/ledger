package io.casehub.ledger.api.model;

import io.casehub.platform.api.identity.IdentityBindingStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityBindingStatusTest {

    @Test
    void allValuesPresent() {
        assertThat(IdentityBindingStatus.values()).containsExactlyInAnyOrder(
                IdentityBindingStatus.VALID,
                IdentityBindingStatus.UNSIGNED,
                IdentityBindingStatus.DID_UNRESOLVABLE,
                IdentityBindingStatus.IDENTITY_MISMATCH,
                IdentityBindingStatus.KEY_MISMATCH,
                IdentityBindingStatus.CREDENTIAL_EXPIRED,
                IdentityBindingStatus.CREDENTIAL_INVALID);
    }
}

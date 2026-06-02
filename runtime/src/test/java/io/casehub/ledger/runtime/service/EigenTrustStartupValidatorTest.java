package io.casehub.ledger.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pure JUnit 5 unit tests for {@link EigenTrustStartupValidator#shouldWarn}.
 * In {@code io.casehub.ledger.runtime.service} to access the package-private static method.
 */
class EigenTrustStartupValidatorTest {

    @Test
    void eigentrustDisabled_noWarn() {
        assertThat(EigenTrustStartupValidator.shouldWarn(false, 0)).isFalse();
    }

    @Test
    void eigentrustEnabled_zeroActors_warns() {
        assertThat(EigenTrustStartupValidator.shouldWarn(true, 0)).isTrue();
    }

    @Test
    void eigentrustEnabled_oneActor_warns() {
        assertThat(EigenTrustStartupValidator.shouldWarn(true, 1)).isTrue();
    }

    @Test
    void eigentrustEnabled_twoActors_warns() {
        assertThat(EigenTrustStartupValidator.shouldWarn(true, 2)).isTrue();
    }

    @Test
    void eigentrustEnabled_threeActors_noWarn() {
        assertThat(EigenTrustStartupValidator.shouldWarn(true, 3)).isFalse();
    }

    @Test
    void eigentrustEnabled_manyActors_noWarn() {
        assertThat(EigenTrustStartupValidator.shouldWarn(true, 10)).isFalse();
    }
}

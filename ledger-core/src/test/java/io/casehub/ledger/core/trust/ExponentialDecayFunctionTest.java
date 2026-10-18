package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.model.AttestationVerdict;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ExponentialDecayFunctionTest {

    @Test
    void soundAttestationDecaysWithBaseHalfLife() {
        var fn = new ExponentialDecayFunction(30, 0.5);
        assertThat(fn.weight(0, AttestationVerdict.SOUND)).isCloseTo(1.0, within(0.001));
        assertThat(fn.weight(30, AttestationVerdict.SOUND)).isCloseTo(0.5, within(0.001));
        assertThat(fn.weight(60, AttestationVerdict.SOUND)).isCloseTo(0.25, within(0.001));
    }

    @Test
    void flaggedAttestationDecaysSlowerThanSound() {
        var fn = new ExponentialDecayFunction(30, 0.5);
        double soundAt30 = fn.weight(30, AttestationVerdict.SOUND);
        double flaggedAt30 = fn.weight(30, AttestationVerdict.FLAGGED);
        assertThat(flaggedAt30).isGreaterThan(soundAt30);
    }

    @Test
    void flaggedHalfLifeIsDoubledWithMultiplierHalf() {
        var fn = new ExponentialDecayFunction(30, 0.5);
        assertThat(fn.weight(60, AttestationVerdict.FLAGGED)).isCloseTo(0.5, within(0.001));
    }

    @Test
    void challengedTreatedSameAsFlagged() {
        var fn = new ExponentialDecayFunction(30, 0.5);
        assertThat(fn.weight(30, AttestationVerdict.CHALLENGED))
                .isCloseTo(fn.weight(30, AttestationVerdict.FLAGGED), within(0.001));
    }

    @Test
    void endorsedTreatedSameAsSound() {
        var fn = new ExponentialDecayFunction(30, 0.5);
        assertThat(fn.weight(30, AttestationVerdict.ENDORSED))
                .isCloseTo(fn.weight(30, AttestationVerdict.SOUND), within(0.001));
    }
}

package io.casehub.ledger.api.model;

import io.casehub.ledger.api.spi.identity.VerificationMethod;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationMethodTest {

    @Test
    void publicKeyBytesIsDefensivelyCopiedOnConstruction() {
        byte[] original = {1, 2, 3};
        var vm = new VerificationMethod("id", "Ed25519", original);
        original[0] = 99;
        assertThat(vm.publicKeyBytes()[0]).isEqualTo((byte) 1);
    }

    @Test
    void getterReturnsDefensiveCopy() {
        var vm = new VerificationMethod("id", "type", new byte[]{1, 2, 3});
        byte[] got = vm.publicKeyBytes();
        got[0] = 99;
        assertThat(vm.publicKeyBytes()[0]).isEqualTo((byte) 1);
    }

    @Test
    void equalsUsesArrayEquals() {
        var a = new VerificationMethod("id", "t", new byte[]{1, 2});
        var b = new VerificationMethod("id", "t", new byte[]{1, 2});
        assertThat(a).isEqualTo(b);
    }
}

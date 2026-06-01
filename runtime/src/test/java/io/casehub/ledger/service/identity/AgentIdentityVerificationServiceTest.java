package io.casehub.ledger.service.identity;

import io.casehub.platform.api.identity.IdentityVerificationResult;
import io.casehub.platform.api.identity.DIDDocument;
import io.casehub.platform.api.identity.VerificationMethod;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.service.identity.AgentIdentityVerificationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentIdentityVerificationServiceTest {

    TestDIDResolver resolver = new TestDIDResolver();
    // Test the platform service directly — the ledger adapter is a trivial field-extraction delegate
    io.casehub.platform.identity.AgentIdentityVerificationService platformSvc =
            new io.casehub.platform.identity.AgentIdentityVerificationService(resolver);
    AgentIdentityVerificationService svc = new AgentIdentityVerificationService(platformSvc);

    LedgerEntry entry(String actorId, String actorDid, byte[] pubKey) {
        var e = new ConcreteEntry();
        e.actorId = actorId;
        e.actorDid = actorDid;
        e.agentPublicKey = pubKey;
        return e;
    }

    @Test
    void returnsUnverifiableWhenNoActorDid() {
        var e = entry("a", null, new byte[]{1});
        assertThat(svc.verifyIdentityBinding(e)).isEqualTo(IdentityVerificationResult.UNVERIFIABLE);
    }

    @Test
    void returnsUnsignedWhenNoPublicKey() {
        var e = entry("a", "did:web:x", null);
        // Register a doc so we know it's not returning UNRESOLVABLE
        resolver.register("did:web:x", new DIDDocument("did:web:x", List.of(), List.of("a")));
        assertThat(svc.verifyIdentityBinding(e)).isEqualTo(IdentityVerificationResult.UNSIGNED);
    }

    @Test
    void returnsDIDUnresolvable() {
        var e = entry("a", "did:web:x", new byte[]{1});
        // No doc registered
        assertThat(svc.verifyIdentityBinding(e)).isEqualTo(IdentityVerificationResult.DID_UNRESOLVABLE);
    }

    @Test
    void returnsIdentityMismatch() {
        resolver.register("did:web:x", new DIDDocument("did:web:x", List.of(), List.of("other")));
        var e = entry("a", "did:web:x", new byte[]{1});
        assertThat(svc.verifyIdentityBinding(e)).isEqualTo(IdentityVerificationResult.IDENTITY_MISMATCH);
    }

    @Test
    void returnsKeyMismatch() {
        var vm = new VerificationMethod("id", "Ed25519", new byte[]{9, 9, 9});
        resolver.register("did:web:x", new DIDDocument("did:web:x", List.of(vm), List.of("a")));
        var e = entry("a", "did:web:x", new byte[]{1, 2, 3});
        assertThat(svc.verifyIdentityBinding(e)).isEqualTo(IdentityVerificationResult.KEY_MISMATCH);
    }

    @Test
    void returnsValid() {
        byte[] key = {1, 2, 3};
        var vm = new VerificationMethod("id", "Ed25519", key);
        resolver.register("did:web:x", new DIDDocument("did:web:x", List.of(vm), List.of("a")));
        var e = entry("a", "did:web:x", key);
        assertThat(svc.verifyIdentityBinding(e)).isEqualTo(IdentityVerificationResult.VALID);
    }

    static class ConcreteEntry extends LedgerEntry {}
}

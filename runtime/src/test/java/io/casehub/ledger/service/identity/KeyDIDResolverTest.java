package io.casehub.ledger.service.identity;

import io.casehub.ledger.runtime.service.identity.KeyDIDResolver;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class KeyDIDResolverTest {

    private final KeyDIDResolver resolver = new KeyDIDResolver();

    /** Builds a did:key string from a key pair using base64url + z prefix (our convention). */
    private String buildDIDKey(byte[] pubEncoded) {
        byte[] multicodec = new byte[pubEncoded.length + 2];
        multicodec[0] = (byte) 0xed;
        multicodec[1] = 0x01;
        System.arraycopy(pubEncoded, 0, multicodec, 2, pubEncoded.length);
        return "did:key:z" + Base64.getUrlEncoder().withoutPadding().encodeToString(multicodec);
    }

    @Test
    void resolvesValidDIDKeyToDocument() throws Exception {
        var gen = KeyPairGenerator.getInstance("Ed25519");
        var keyPair = gen.generateKeyPair();
        var did = buildDIDKey(keyPair.getPublic().getEncoded());

        var result = resolver.resolve(did);
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(did);
        assertThat(result.get().verificationMethods()).hasSize(1);
        assertThat(result.get().verificationMethods().get(0).publicKeyBytes())
            .isEqualTo(keyPair.getPublic().getEncoded()); // key bytes match
    }

    @Test
    void alsoKnownAsIsAlwaysEmpty() throws Exception {
        var gen = KeyPairGenerator.getInstance("Ed25519");
        var did = buildDIDKey(gen.generateKeyPair().getPublic().getEncoded());
        assertThat(resolver.resolve(did).get().alsoKnownAs()).isEmpty();
    }

    @Test
    void returnsEmptyForNonDIDKeyMethod() {
        assertThat(resolver.resolve("did:web:example.com")).isEmpty();
        assertThat(resolver.resolve("did:ethr:0xabc")).isEmpty();
    }

    @Test
    void returnsEmptyForMalformedKey() {
        assertThat(resolver.resolve("did:key:NOT_BASE64URL!!")).isEmpty();
    }

    @Test
    void returnsEmptyForNull() {
        assertThat(resolver.resolve(null)).isEmpty();
    }
}

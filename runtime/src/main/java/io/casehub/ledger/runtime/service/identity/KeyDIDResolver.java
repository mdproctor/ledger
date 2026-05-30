package io.casehub.ledger.runtime.service.identity;

import io.casehub.ledger.api.spi.identity.DIDDocument;
import io.casehub.ledger.api.spi.identity.VerificationMethod;
import io.casehub.ledger.api.spi.resolve.DIDResolver;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Resolves did:key DIDs by decoding key material directly from the DID string.
 * Standards note: the real did:key spec uses base58btc multibase encoding.
 * This implementation uses base64url for consistency with Java's standard library.
 *
 * Does NOT produce alsoKnownAs entries — did:key documents are deterministic from key bytes only.
 * Use TestDIDResolver in tests that require alsoKnownAs.
 */
@ApplicationScoped
public class KeyDIDResolver implements DIDResolver {

    private static final Logger LOG = Logger.getLogger(KeyDIDResolver.class);
    private static final String DID_KEY_PREFIX = "did:key:";

    @Override
    public Optional<DIDDocument> resolve(final String did) {
        if (did == null || !did.startsWith(DID_KEY_PREFIX)) return Optional.empty();
        try {
            final String keyPart = did.substring(DID_KEY_PREFIX.length());
            if (!keyPart.startsWith("z")) return Optional.empty();
            // Decode: 'z' prefix + base64url-encoded (2-byte multicodec prefix + raw key bytes)
            final byte[] multicodec = Base64.getUrlDecoder().decode(keyPart.substring(1));
            if (multicodec.length < 2) return Optional.empty();
            // Strip 2-byte multicodec prefix to get raw key bytes
            final byte[] keyBytes = new byte[multicodec.length - 2];
            System.arraycopy(multicodec, 2, keyBytes, 0, keyBytes.length);
            final String vmId = did + "#" + keyPart;
            final var vm = new VerificationMethod(vmId, "Ed25519VerificationKey2020", keyBytes);
            return Optional.of(new DIDDocument(did, List.of(vm), List.of()));
        } catch (final Exception e) {
            LOG.debugf("KeyDIDResolver: failed to decode %s: %s", did, e.getMessage());
            return Optional.empty();
        }
    }
}

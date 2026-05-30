package io.casehub.ledger.api.spi.identity;

import java.util.List;

/**
 * A resolved DID document.
 *
 * <p>
 * Carries the DID subject identifier, its associated verification methods (public keys),
 * and any {@code alsoKnownAs} aliases. All list fields are immutable and never null.
 */
public record DIDDocument(
        String id,
        List<VerificationMethod> verificationMethods,
        List<String> alsoKnownAs) {

    /** Null-safe, immutable copies of both list fields. */
    public DIDDocument {
        verificationMethods = verificationMethods == null
                ? List.of()
                : List.copyOf(verificationMethods);
        alsoKnownAs = alsoKnownAs == null
                ? List.of()
                : List.copyOf(alsoKnownAs);
    }
}

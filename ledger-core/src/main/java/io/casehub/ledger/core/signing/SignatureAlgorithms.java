package io.casehub.ledger.core.signing;

import java.security.Key;
import java.security.interfaces.ECKey;

public final class SignatureAlgorithms {

    private SignatureAlgorithms() {}

    public static String signatureAlgorithm(final Key key) {
        if (!"EC".equals(key.getAlgorithm())) {
            return key.getAlgorithm();
        }
        final ECKey ec = (ECKey) key;
        return switch (ec.getParams().getOrder().bitLength()) {
            case 256 -> "SHA256withECDSA";
            case 384 -> "SHA384withECDSA";
            case 521 -> "SHA512withECDSA";
            default -> throw new IllegalArgumentException(
                    "Unsupported EC curve order: " + ec.getParams().getOrder().bitLength());
        };
    }
}

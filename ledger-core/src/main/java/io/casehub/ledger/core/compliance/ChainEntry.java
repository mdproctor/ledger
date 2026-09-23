package io.casehub.ledger.core.compliance;

public record ChainEntry(
        int sequenceNumber,
        String digest) {
}

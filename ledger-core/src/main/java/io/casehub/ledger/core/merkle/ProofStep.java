package io.casehub.ledger.core.merkle;

public record ProofStep(String hash, Side side) {

    public enum Side {
        LEFT,
        RIGHT
    }
}

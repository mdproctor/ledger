package io.casehub.ledger.core.config;

public record IdentityProperties(TokenisationProperties tokenisation) {

    public record TokenisationProperties(boolean enabled) {
        public static TokenisationProperties defaults() {
            return new TokenisationProperties(false);
        }
    }

    public static IdentityProperties defaults() {
        return new IdentityProperties(TokenisationProperties.defaults());
    }
}

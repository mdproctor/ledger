package io.casehub.ledger.core.privacy;

public interface ContentSanitiser {

    String sanitise(String decisionContextJson);
}

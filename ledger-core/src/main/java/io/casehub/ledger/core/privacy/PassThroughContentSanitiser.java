package io.casehub.ledger.core.privacy;

public class PassThroughContentSanitiser implements ContentSanitiser {

    @Override
    public String sanitise(final String decisionContextJson) {
        return decisionContextJson;
    }
}

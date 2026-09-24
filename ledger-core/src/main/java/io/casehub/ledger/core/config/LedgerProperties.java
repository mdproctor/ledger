package io.casehub.ledger.core.config;

import java.util.Optional;

public record LedgerProperties(
        boolean enabled,
        Optional<String> datasource,
        HashChainProperties hashChain,
        DecisionContextProperties decisionContext,
        EvidenceProperties evidence,
        AttestationProperties attestations,
        TrustScoreProperties trustScore,
        RetentionProperties retention,
        MerkleProperties merkle,
        IdentityProperties identity,
        DecayProperties decay,
        HealthProperties health,
        AgentSigningProperties agentSigning,
        OutcomeProperties outcome,
        ErasureReceiptProperties erasureReceipt,
        MetadataProperties metadata,
        AgentIdentityProperties agentIdentity
) {
    public static LedgerProperties defaults() {
        return new LedgerProperties(
                true, Optional.empty(),
                HashChainProperties.defaults(),
                DecisionContextProperties.defaults(),
                EvidenceProperties.defaults(),
                AttestationProperties.defaults(),
                TrustScoreProperties.defaults(),
                RetentionProperties.defaults(),
                MerkleProperties.defaults(),
                IdentityProperties.defaults(),
                DecayProperties.defaults(),
                HealthProperties.defaults(),
                AgentSigningProperties.defaults(),
                OutcomeProperties.defaults(),
                ErasureReceiptProperties.defaults(),
                MetadataProperties.defaults(),
                AgentIdentityProperties.defaults()
        );
    }
}

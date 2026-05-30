package io.casehub.ledger.api.model;

/**
 * Read-path result from {@code AgentIdentityVerificationService}.
 * Verifies that the stored {@code agentPublicKey} matches a verification method
 * in the current DID document for the entry's {@code actorDid}.
 *
 * <p>Does NOT re-run {@code AgentCredentialValidator} — VC results are stored at write
 * time in {@code ActorIdentityBindingEntry}. Consumers needing write-time VC results
 * should query {@code ActorIdentityBindingRepository.latestBindingFor(actorId)}.
 */
public enum IdentityVerificationResult {
    VALID,              // public key matches DID document; alsoKnownAs confirmed
    UNVERIFIABLE,       // no actorDid on entry — actor has no DID binding
    UNSIGNED,           // no agentPublicKey on entry — nothing to cross-check
    DID_UNRESOLVABLE,   // resolver returned empty (network failure or DID not found)
    IDENTITY_MISMATCH,  // DID document alsoKnownAs does not include actorId
    KEY_MISMATCH        // entry key no longer in DID document (key rotated since entry written)
}

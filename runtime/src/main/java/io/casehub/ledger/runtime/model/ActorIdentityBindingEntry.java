package io.casehub.ledger.runtime.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

import io.casehub.platform.api.identity.CredentialValidationResult;
import io.casehub.platform.api.identity.IdentityBindingStatus;

/**
 * A first-class immutable ledger entry recording an actor DID identity binding event.
 *
 * <p>
 * {@link #subjectId} is derived deterministically as
 * {@code UUID.nameUUIDFromBytes(actorId.getBytes(UTF_8))}, making the full
 * identity binding history for an actor queryable as an ordered ledger sequence —
 * identical to the convention used by {@link KeyRotationEntry}.
 *
 * <p>
 * {@link #entryType} is set to {@link io.casehub.ledger.api.model.LedgerEntryType#EVENT}
 * by {@code ActorIdentityBindingObserver} before persist.
 *
 * <p>
 * Each entry records the outcome of a DID binding validation attempt: whether the
 * actor's claimed DID could be resolved, whether the agent's signing key appears in
 * the DID document's {@code alsoKnownAs} / verification methods, and — when a
 * Verifiable Credential was presented — the credential validation outcome.
 */
@NamedQueries({
    @NamedQuery(
        name = "ActorIdentityBindingEntry.findLatestByActorId",
        query = "SELECT e FROM ActorIdentityBindingEntry e WHERE e.actorId = :actorId ORDER BY e.occurredAt DESC"),
    @NamedQuery(
        name = "ActorIdentityBindingEntry.findHistoryByActorId",
        query = "SELECT e FROM ActorIdentityBindingEntry e WHERE e.actorId = :actorId ORDER BY e.occurredAt ASC")
})
@Entity
@Table(name = "actor_identity_binding")
@DiscriminatorValue("IDENTITY_BINDING")
public class ActorIdentityBindingEntry extends LedgerEntry {

    /** The DID URI claimed by the actor at bind time. */
    @Column(name = "bound_did", nullable = false)
    public String boundDid;

    /** Overall validation outcome for this binding attempt. */
    @Enumerated(EnumType.STRING)
    @Column(name = "validation_result", nullable = false)
    public IdentityBindingStatus validationResult;

    /**
     * Whether the actor's identifier (actorId) appears in the DID document's
     * {@code alsoKnownAs} array, confirming the actor controls the claimed DID.
     */
    @Column(name = "also_known_as_verified", nullable = false)
    public boolean alsoKnownAsVerified;

    /**
     * Whether the agent's current signing key ({@code agentKeyRef}) was found in
     * the DID document's verification methods, confirming cryptographic ownership.
     */
    @Column(name = "key_match_verified", nullable = false)
    public boolean keyMatchVerified;

    /**
     * The {@code keyRef} (Base64URL SHA-256 of public key) that was matched in
     * the DID document's verification methods.
     * Null when {@link #keyMatchVerified} is {@code false}.
     */
    @Column(name = "verified_key_ref")
    public String verifiedKeyRef;

    /**
     * Verifiable Credential validation outcome, when a credential was presented.
     * Null when no credential was evaluated.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "credential_result")
    public CredentialValidationResult credentialResult;

    /**
     * DID method extracted from {@link #boundDid} — e.g. {@code "web"}, {@code "key"}.
     * Stored for auditing and method-level trust policy enforcement.
     * Null if the DID could not be parsed.
     */
    @Column(name = "did_method", length = 32)
    public String didMethod;

    @Override
    protected byte[] domainContentBytes() {
        String content = String.join("|",
            boundDid != null ? boundDid : "",
            validationResult != null ? validationResult.name() : "",
            String.valueOf(alsoKnownAsVerified),
            String.valueOf(keyMatchVerified),
            verifiedKeyRef != null ? verifiedKeyRef : "",
            credentialResult != null ? credentialResult.name() : "",
            didMethod != null ? didMethod : ""
        );
        return content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}

package io.casehub.ledger.memory;

import java.util.UUID;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.platform.api.identity.ActorType;

/**
 * Accessor for {@link LedgerAttestation} fields from test code.
 *
 * <p>
 * Quarkus bytecode enhancement changes {@code @Entity} public fields to {@code protected}
 * in the augmented classloader. Field reads from a non-subclass context cause
 * {@link IllegalAccessError}. This class extends {@link LedgerAttestation} so that
 * protected field reads are valid, and exposes them via static helper methods
 * (using a cast) and a factory method for creating test fixtures.
 */
public class LedgerAttestationAccessor extends LedgerAttestation {

    /** Create a test attestation. Field assignment runs inside this subclass. */
    public static LedgerAttestation create(final UUID entryId, final UUID subjectId,
            final String attestorId, final String capabilityTag) {
        final LedgerAttestationAccessor a = new LedgerAttestationAccessor();
        a.ledgerEntryId = entryId;
        a.subjectId = subjectId;
        a.attestorId = attestorId;
        a.attestorType = ActorType.AGENT;
        a.verdict = AttestationVerdict.SOUND;
        a.confidence = 1.0;
        a.capabilityTag = capabilityTag;
        return a;
    }

    /** Create a test attestation with explicit attestorType. */
    public static LedgerAttestation create(final UUID entryId, final UUID subjectId,
            final String attestorId, final String capabilityTag, final ActorType attestorType) {
        final LedgerAttestationAccessor a = new LedgerAttestationAccessor();
        a.ledgerEntryId = entryId;
        a.subjectId = subjectId;
        a.attestorId = attestorId;
        a.attestorType = attestorType;
        a.verdict = AttestationVerdict.SOUND;
        a.confidence = 1.0;
        a.capabilityTag = capabilityTag;
        return a;
    }

    /** Read {@code attestorId} via subclass context. */
    public static String attestorId(final LedgerAttestation a) {
        return ((LedgerAttestationAccessor) a).attestorId;
    }

    /** Read {@code capabilityTag} via subclass context. */
    public static String capabilityTag(final LedgerAttestation a) {
        return ((LedgerAttestationAccessor) a).capabilityTag;
    }
}

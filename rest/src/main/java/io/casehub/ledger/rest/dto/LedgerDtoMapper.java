package io.casehub.ledger.rest.dto;

import java.util.List;

import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.runtime.service.model.InclusionProof;

public final class LedgerDtoMapper {

    private LedgerDtoMapper() {
    }

    public static LedgerEntryResponse toResponse(final LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.id,
                entry.subjectId,
                entry.tenancyId,
                entry.sequenceNumber,
                entry.entryType != null ? entry.entryType.name() : null,
                entry.actorId,
                entry.actorType != null ? entry.actorType.name() : null,
                entry.actorRole,
                entry.occurredAt,
                entry.digest,
                entry.traceId,
                entry.causedByEntryId,
                entry.metadata);  // LedgerEntry.metadata → response.payload (blocks-ui convention)
    }

    public static List<LedgerEntryResponse> toResponseList(final List<? extends LedgerEntry> entries) {
        return entries.stream().map(LedgerDtoMapper::toResponse).toList();
    }

    public static AttestationResponse toResponse(final LedgerAttestation attestation) {
        return new AttestationResponse(
                attestation.id,
                attestation.ledgerEntryId,
                attestation.subjectId,
                attestation.attestorId,
                attestation.attestorType != null ? attestation.attestorType.name() : null,
                attestation.attestorRole,
                attestation.verdict != null ? attestation.verdict.name() : null,
                attestation.evidence,
                attestation.confidence,
                attestation.capabilityTag,
                attestation.trustDimension,
                attestation.dimensionScore,
                attestation.occurredAt);
    }

    public static List<AttestationResponse> toAttestationList(final List<LedgerAttestation> attestations) {
        return attestations.stream().map(LedgerDtoMapper::toResponse).toList();
    }

    public static InclusionProofResponse toResponse(final InclusionProof proof) {
        final List<InclusionProofResponse.ProofStepResponse> steps = proof.siblings().stream()
                .map(s -> new InclusionProofResponse.ProofStepResponse(s.hash(), s.side().name()))
                .toList();
        return new InclusionProofResponse(
                proof.entryId(),
                proof.entryIndex(),
                proof.treeSize(),
                proof.leafHash(),
                steps,
                proof.treeRoot());
    }
}

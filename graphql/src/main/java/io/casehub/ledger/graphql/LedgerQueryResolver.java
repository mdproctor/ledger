package io.casehub.ledger.graphql;

import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.graphql.dto.LedgerAttestationType;
import io.casehub.ledger.graphql.dto.LedgerEntryFilterInput;
import io.casehub.ledger.graphql.dto.LedgerEntryPage;
import io.casehub.ledger.graphql.dto.LedgerEntryType;
import io.casehub.ledger.graphql.dto.MerkleVerificationType;
import io.casehub.ledger.graphql.dto.TrustCapabilityScoreType;
import io.casehub.ledger.graphql.dto.TrustRoutingProfileType;
import io.casehub.ledger.graphql.dto.TrustScoreType;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.graphql.PageInfo;
import io.casehub.platform.graphql.PageInput;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Query;

@GraphQLApi
@McpDomain(value = "ledger/model", app = "ledger", summary = "Ledger domain model enrichment and state")
@ApplicationScoped
public class LedgerQueryResolver {

    @Inject LedgerEntryRepository entryRepository;
    @Inject TrustScoreSource trustScoreSource;
    @Inject LedgerVerificationService verificationService;
    @Inject CurrentPrincipal currentPrincipal;

    @Query
    @Description("List ledger entries with optional filtering by subject, actor, role, and time range")
    public LedgerEntryPage ledgerEntries(LedgerEntryFilterInput filter, PageInput page) {
        int offset = page != null && page.offset() != null ? page.offset() : 0;
        int limit = page != null && page.limit() != null ? page.limit() : 20;
        String tenancyId = currentPrincipal.tenancyId();

        List<LedgerEntry> entries;
        if (filter != null && filter.subjectId() != null) {
            if (filter.from() != null && filter.to() != null) {
                entries = entryRepository.findBySubjectIdAndTimeRange(
                        filter.subjectId(), filter.from(), filter.to(), tenancyId);
            } else {
                entries = entryRepository.findBySubjectId(filter.subjectId(), tenancyId);
            }
        } else if (filter != null && filter.actorId() != null
                && filter.from() != null && filter.to() != null) {
            entries = entryRepository.findByActorId(
                    filter.actorId(), filter.from(), filter.to(), tenancyId);
        } else if (filter != null && filter.actorRole() != null
                && filter.from() != null && filter.to() != null) {
            entries = entryRepository.findByActorRole(
                    filter.actorRole(), filter.from(), filter.to(), tenancyId);
        } else {
            entries = List.of();
        }

        List<LedgerEntryType> all = entries.stream().map(LedgerEntryType::from).toList();
        int total = all.size();
        int end = Math.min(offset + limit, total);
        List<LedgerEntryType> items = offset < total ? all.subList(offset, end) : List.of();

        return new LedgerEntryPage(items, new PageInfo(end < total, offset > 0, total, null));
    }

    @Query
    @Description("Retrieve a single ledger entry by its unique identifier")
    public LedgerEntryType ledgerEntry(UUID id) {
        return entryRepository.findEntryById(id, currentPrincipal.tenancyId())
                .map(LedgerEntryType::from)
                .orElse(null);
    }

    @Query
    @Description("Retrieve attestations for a ledger entry, optionally filtered by capability tag")
    public List<LedgerAttestationType> ledgerAttestations(UUID entryId, String capabilityTag) {
        String tenancyId = currentPrincipal.tenancyId();
        List<LedgerAttestation> attestations;
        if (capabilityTag != null) {
            attestations = entryRepository.findAttestationsByEntryIdAndCapabilityTag(
                    entryId, capabilityTag, tenancyId);
        } else {
            attestations = entryRepository.findAttestationsByEntryId(entryId, tenancyId);
        }
        return attestations.stream().map(LedgerAttestationType::from).toList();
    }

    @Query
    @Description("Global trust score for an actor — aggregate across all capabilities and dimensions")
    public TrustScoreType trustScore(String actorId) {
        return new TrustScoreType(
                actorId,
                trustScoreSource.globalScore(actorId).stream().boxed().findFirst().orElse(null),
                trustScoreSource.allCapabilityScores(actorId),
                trustScoreSource.allDimensionScores(actorId));
    }

    @Query
    @Description("Capability-scoped trust score for an actor — score, decision count, and quality dimensions")
    public TrustCapabilityScoreType trustCapabilityScore(String actorId, String capabilityTag) {
        return new TrustCapabilityScoreType(
                actorId,
                capabilityTag,
                trustScoreSource.capabilityScore(actorId, capabilityTag)
                        .stream().boxed().findFirst().orElse(null),
                trustScoreSource.decisionCount(actorId, capabilityTag),
                trustScoreSource.qualityScores(actorId, capabilityTag));
    }

    @Query
    @Description("Composite trust routing profile — global score, capability score, decision count, "
            + "and quality dimensions in one call. Replaces multiple individual trust queries.")
    public TrustRoutingProfileType trustRoutingProfile(String actorId, String capabilityTag) {
        return new TrustRoutingProfileType(
                actorId,
                capabilityTag,
                trustScoreSource.globalScore(actorId).stream().boxed().findFirst().orElse(null),
                trustScoreSource.capabilityScore(actorId, capabilityTag)
                        .stream().boxed().findFirst().orElse(null),
                trustScoreSource.decisionCount(actorId, capabilityTag),
                trustScoreSource.qualityScores(actorId, capabilityTag));
    }

    @Query
    @Description("Verify Merkle tree integrity for all entries of a subject — returns tree root and verification status")
    public MerkleVerificationType merkleVerification(UUID subjectId) {
        String tenancyId = currentPrincipal.tenancyId();
        boolean verified = verificationService.verify(subjectId, tenancyId);
        String treeRoot = verified ? verificationService.treeRoot(subjectId, tenancyId) : null;
        return new MerkleVerificationType(subjectId, treeRoot, verified);
    }
}

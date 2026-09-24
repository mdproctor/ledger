package io.casehub.ledger.rest.api;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerAppender;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.view.AppendEntryRequest;
import io.casehub.ledger.api.view.LedgerEntryPage;
import io.casehub.ledger.api.view.LedgerEntryView;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@McpDomain(value = "ledger/entries", basePath = "/api/v1/ledger")
@ApplicationScoped
public class DefaultLedgerEntryApi {

    private final io.casehub.ledger.core.api.LedgerEntryApiCore core;

    @Inject
    DefaultLedgerEntryApi(LedgerEntryRepository repository, LedgerAppender appender) {
        this.core = new io.casehub.ledger.core.api.LedgerEntryApiCore(repository, appender);
    }

    @PlatformQuery("List ledger entries by subject or actor with optional time range")
    public LedgerEntryPage listEntries(UUID subjectId, String actorId,
                                       @ContextParam("tenancyId") String tenancyId,
                                       Instant from, Instant to,
                                       Integer offset, Integer limit) {
        return core.listEntries(subjectId, actorId, tenancyId, from, to, offset, limit);
    }

    @PlatformQuery("Get a single ledger entry by ID")
    public LedgerEntryView getEntry(@PathParam UUID id, @ContextParam("tenancyId") String tenancyId) {
        return core.getEntry(id, tenancyId);
    }

    @PlatformQuery("Get entries causally triggered by this entry")
    public List<LedgerEntryView> getCausedBy(@PathParam UUID id, @ContextParam("tenancyId") String tenancyId) {
        return core.getCausedBy(id, tenancyId);
    }

    @PlatformMutation("Append a new audit entry to the ledger")
    public LedgerEntryView appendEntry(AppendEntryRequest request, @ContextParam("tenancyId") String tenancyId) {
        return core.appendEntry(request, tenancyId);
    }

    static LedgerEntryView toView(LedgerEntry e) {
        return io.casehub.ledger.core.api.LedgerEntryApiCore.toView(e);
    }

    static String defaultTenancyId(String tenancyId) {
        return io.casehub.ledger.core.api.LedgerEntryApiCore.defaultTenancyId(tenancyId);
    }
}

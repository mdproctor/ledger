## D1: PDF rendering approach

**Choice:** Use platform's existing `PdfGenerator` SPI (`io.casehub.platform.api.pdf.PdfGenerator`)
**Alternatives:**
- OpenPDF in ledger — duplicates platform capability, adds transitive dep to all consumers
- iText — commercial license, unnecessary given platform already solved this
**Rationale:** Platform already provides `PdfGenerator.generateFromHtml(html, PdfOptions)` backed by `openhtmltopdf` + PDFBox. `casehub-platform-api` is already a dependency of `casehub-ledger-api`. No new dependencies needed.
**Trade-offs:** Ledger depends on platform's rendering quality and PDF/A conformance settings; cannot customise PDF rendering independently
**Sources:** `platform-api/.../pdf/PdfGenerator.java`, `platform-pdf/.../OpenHtmlToPdfGenerator.java`
**Exploration:** quick
**Status:** captured

## D2: Module placement

**Choice:** New `casehub-ledger-reporting` module (plain JAR, not a Quarkus extension)
**Alternatives:**
- In `runtime` module, config-gated — simpler but forces Qute on all consumers
- SPI in `api`, impl in new module — over-engineered, can extract later if needed
**Rationale:** Follows existing pattern (`rest/`, `graphql/` are optional plain JARs). Keeps core extension lean. Consumers opt in by adding `casehub-ledger-reporting` to their pom.
**Trade-offs:** One more module to maintain; consumers must explicitly add the dependency
**Sources:** `rest/`, `graphql/` module patterns
**Exploration:** quick
**Status:** captured

## D3: HTML template engine

**Choice:** Qute (`quarkus-qute`) in the reporting module
**Alternatives:**
- Programmatic HTML (StringBuilder) — no dependency but unmaintainable for structured audit reports
**Rationale:** Audit-grade reports need structured tables, headers, and styling. Qute is Quarkus-native, type-safe, supports template inheritance and classpath overrides. Dependency is contained in the opt-in reporting module.
**Trade-offs:** Adds Qute as a transitive dependency for reporting consumers
**Sources:** Issue #211 proposal
**Exploration:** quick
**Status:** captured

## D4: Tenancy-level query

**Choice:** Add `findByTenancyIdAndTimeRange(String tenancyId, Instant from, Instant to)` to `LedgerEntryRepository`
**Alternatives:**
- Use `CrossTenantLedgerEntryRepository` with tenancy filter — semantically wrong; cross-tenant repo is for system-level operations, not tenant-scoped reads
- Compose from existing queries (find subjects, then per-subject queries) — O(N) database calls, unacceptable for large tenancies
**Rationale:** This is a legitimate tenant-scoped read. Every `LedgerEntryRepository` method takes `tenancyId`; this follows the same pattern. Time range parameter prevents unbounded result sets.
**Trade-offs:** New SPI method requires implementation in JPA, in-memory, and no-op repos
**Sources:** `api/.../spi/LedgerEntryRepository.java`, `per-subject-table-tenancy.md` protocol
**Exploration:** quick
**Status:** captured

## D5: Service layer split

**Choice:** Data services in `runtime`, presentation in `reporting` module
**Alternatives:**
- All new services in `reporting` — duplicates query/aggregation patterns already in runtime
**Rationale:** Existing `LedgerComplianceReportService` already lives in runtime with repo access. Add `reportForTenancy()` there and create `AuditTrailExportService` alongside it. The reporting module is purely presentation: injects data services + `PdfGenerator`, renders HTML via Qute, provides content negotiation helper.
**Trade-offs:** Reporting module has a thinner role; runtime gains two more service methods
**Sources:** `runtime/.../service/LedgerComplianceReportService.java`
**Exploration:** quick
**Status:** captured

## D6: Report model evolution

**Choice:** Evolve the existing `ComplianceReport` and `DecisionRecord` in `ledger-core`
**Alternatives:**
- New model, deprecate old — creates churn for no benefit (pre-release, no deployed consumers)
- Separate models per scope (per-subject vs tenancy) — unnecessary fragmentation; same data, different aggregation level
**Rationale:** Pre-release project with no deployed consumers. Enriching the existing record is free — add `ComplianceSummary`, enhance `DecisionRecord` with `planRef`, `rationale`, `detail` from `ComplianceSupplement`. The existing `format()` method gets richer output. `AuditTrailExport` and related records are net-new (no existing equivalent).
**Trade-offs:** None — pre-release, no backward compatibility concern
**Sources:** `ledger-core/.../compliance/ComplianceReport.java`, `ledger-core/.../compliance/DecisionRecord.java`
**Exploration:** quick
**Status:** captured

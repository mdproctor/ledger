package io.casehub.ledger.spring;

import io.casehub.ledger.api.model.AuditRecord;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.OutcomeRecord;
import io.casehub.ledger.api.spi.ActorIdentityProvider;
import io.casehub.ledger.api.spi.LedgerAppender;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.spi.LedgerMerkleFrontierRepository;
import io.casehub.ledger.api.spi.LedgerTraceIdProvider;
import io.casehub.ledger.core.config.LedgerProperties;
import io.casehub.ledger.core.enricher.EnricherPipelineCore;
import io.casehub.ledger.core.enricher.LedgerEntryEnricher;
import io.casehub.ledger.core.privacy.ContentSanitiser;
import io.casehub.ledger.core.privacy.PassThroughActorIdentityProvider;
import io.casehub.ledger.core.privacy.PassThroughContentSanitiser;
import io.casehub.ledger.core.service.ComplianceReportServiceCore;
import io.casehub.ledger.core.service.LedgerAppenderCore;
import io.casehub.ledger.core.service.MerklePublisherCore;
import io.casehub.ledger.core.service.OutcomeRecordSaveCore;
import io.casehub.ledger.core.service.OutcomeRecorderCore;
import io.casehub.ledger.core.service.ProvExportServiceCore;
import io.casehub.ledger.core.service.SignatureVerificationCore;
import io.casehub.ledger.core.service.TraceIdEnricherCore;
import io.casehub.ledger.core.service.VerificationServiceCore;
import io.casehub.ledger.core.service.identity.ActorDIDEnricherCore;
import io.casehub.ledger.core.service.identity.IdentityValidationEnricherCore;
import io.casehub.ledger.core.repository.NoOpActorTrustScoreRepository;
import io.casehub.ledger.core.repository.NoOpLedgerEntryRepository;
import io.casehub.ledger.core.repository.NoOpLedgerMerkleFrontierRepository;
import io.casehub.ledger.core.repository.NoOpTrustScoreSnapshotRepository;
import io.casehub.ledger.core.federation.NoOpTrustBootstrapSource;
import io.casehub.ledger.core.federation.NoOpTrustImportService;
import io.casehub.ledger.core.federation.TrustBootstrapSource;
import io.casehub.ledger.core.federation.TrustImportService;
import io.casehub.ledger.core.trust.AllAttestationsGlobalStrategy;
import io.casehub.ledger.core.trust.DecayFunction;
import io.casehub.ledger.core.trust.ExponentialDecayFunction;
import io.casehub.ledger.core.trust.GlobalScoreStrategy;
import io.casehub.ledger.core.trust.NoOpAttestorCredibilityPolicy;
import io.casehub.ledger.jpa.PlainLedgerEntry;
import io.casehub.platform.api.identity.ActorDIDProvider;
import io.casehub.platform.api.identity.AgentCredentialValidator;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.DIDResolver;
import io.casehub.platform.api.identity.IdentityBindingStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

@AutoConfiguration
@AutoConfigureBefore(name = "io.casehub.ledger.runtime.spring.RuntimeAutoConfiguration")
@EnableConfigurationProperties(LedgerConfigurationProperties.class)
public class LedgerManualConfig {

    private static final Logger log = Logger.getLogger(LedgerManualConfig.class.getName());

    @Bean
    LedgerProperties ledgerProperties(LedgerConfigurationProperties config) {
        return config.toProperties();
    }

    // --- Enricher pipeline ---

    @Bean
    EnricherPipelineCore enricherPipelineCore(ObjectProvider<LedgerEntryEnricher> enrichers) {
        return new EnricherPipelineCore(enrichers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnBean(LedgerTraceIdProvider.class)
    TraceIdEnricherCore traceIdEnricherCore(LedgerTraceIdProvider provider) {
        return new TraceIdEnricherCore(provider);
    }

    @Bean
    @ConditionalOnBean(ActorDIDProvider.class)
    ActorDIDEnricherCore actorDIDEnricherCore(ActorDIDProvider provider) {
        return new ActorDIDEnricherCore(provider);
    }

    @Bean
    @ConditionalOnBean({DIDResolver.class, AgentCredentialValidator.class})
    IdentityValidationEnricherCore identityValidationEnricherCore(
            DIDResolver resolver, AgentCredentialValidator validator) {
        return new IdentityValidationEnricherCore(resolver, validator,
                (entry, status) -> {
                    if (status != IdentityBindingStatus.VALID) {
                        log.log(Level.WARNING, "Identity binding {0} for entry {1}",
                                new Object[]{status, entry.id});
                    }
                });
    }

    // --- Core services ---

    @Bean
    VerificationServiceCore verificationServiceCore(LedgerEntryRepository repo,
                                                     LedgerMerkleFrontierRepository frontierRepo) {
        return new VerificationServiceCore(repo, frontierRepo);
    }

    @Bean
    ComplianceReportServiceCore complianceReportServiceCore(LedgerEntryRepository repo,
                                                            VerificationServiceCore verificationService) {
        return new ComplianceReportServiceCore(repo, verificationService);
    }

    @Bean
    ProvExportServiceCore provExportServiceCore(LedgerEntryRepository repo) {
        return new ProvExportServiceCore(repo);
    }

    @Bean
    MerklePublisherCore merklePublisherCore(LedgerProperties props) {
        return new MerklePublisherCore(props.merkle());
    }

    @Bean
    SignatureVerificationCore signatureVerificationCore(LedgerEntryRepository repo) {
        return new SignatureVerificationCore(repo, (actorId, tenancyId) -> Collections.emptyList());
    }

    // --- Default beans (correct NoOp implementations — overrides broken generated code) ---

    @Bean
    @ConditionalOnMissingBean(DecayFunction.class)
    DecayFunction exponentialDecayFunction(LedgerProperties props) {
        return new ExponentialDecayFunction(
                props.trustScore().decayHalfLifeDays(),
                props.decay().flaggedPersistenceMultiplier());
    }

    @Bean
    @ConditionalOnMissingBean(GlobalScoreStrategy.class)
    GlobalScoreStrategy allAttestationsGlobalStrategy() {
        return new AllAttestationsGlobalStrategy();
    }

    @Bean
    @ConditionalOnMissingBean(LedgerEntryRepository.class)
    LedgerEntryRepository noOpLedgerEntryRepository() {
        return new NoOpLedgerEntryRepository();
    }

    @Bean
    @ConditionalOnMissingBean(io.casehub.ledger.api.spi.AttestorCredibilityPolicy.class)
    io.casehub.ledger.api.spi.AttestorCredibilityPolicy noOpAttestorCredibilityPolicy() {
        return new NoOpAttestorCredibilityPolicy();
    }

    @Bean
    @ConditionalOnMissingBean(TrustImportService.class)
    TrustImportService noOpTrustImportService() {
        return new NoOpTrustImportService();
    }

    @Bean
    @ConditionalOnMissingBean(TrustBootstrapSource.class)
    TrustBootstrapSource noOpTrustBootstrapSource() {
        return new NoOpTrustBootstrapSource();
    }

    @Bean
    @ConditionalOnMissingBean(io.casehub.ledger.api.spi.ActorTrustScoreRepository.class)
    io.casehub.ledger.api.spi.ActorTrustScoreRepository noOpActorTrustScoreRepository() {
        return new NoOpActorTrustScoreRepository();
    }

    @Bean
    @ConditionalOnMissingBean(LedgerMerkleFrontierRepository.class)
    LedgerMerkleFrontierRepository noOpLedgerMerkleFrontierRepository() {
        return new NoOpLedgerMerkleFrontierRepository();
    }

    @Bean
    @ConditionalOnMissingBean(io.casehub.ledger.api.spi.TrustScoreSnapshotRepository.class)
    io.casehub.ledger.api.spi.TrustScoreSnapshotRepository noOpTrustScoreSnapshotRepository() {
        return new NoOpTrustScoreSnapshotRepository();
    }

    // --- Appender + Outcome ---

    @Bean
    @ConditionalOnMissingBean(LedgerAppender.class)
    LedgerAppenderCore ledgerAppenderCore(LedgerEntryRepository repo, LedgerProperties props) {
        Function<AuditRecord, LedgerEntry> factory = record -> {
            PlainLedgerEntry entry = new PlainLedgerEntry();
            entry.actorId = record.actorId();
            entry.actorType = record.actorType();
            entry.actorRole = record.actorRole();
            entry.subjectId = record.subjectId();
            entry.entryType = record.entryType();
            entry.occurredAt = record.occurredAt();
            entry.causedByEntryId = record.causedByEntryId();
            entry.metadata = record.metadata();
            entry.domainData = record.domainData();
            return entry;
        };
        return new LedgerAppenderCore(repo, props.metadata(), factory);
    }

    @Bean
    OutcomeRecordSaveCore outcomeRecordSaveCore(LedgerEntryRepository repo, LedgerProperties props) {
        Function<OutcomeRecord, LedgerEntry> factory = record -> {
            PlainLedgerEntry entry = new PlainLedgerEntry();
            entry.actorId = record.actorId();
            entry.actorType = record.actorType();
            entry.actorRole = record.actorRole();
            entry.subjectId = record.subjectId();
            entry.entryType = record.entryType();
            entry.occurredAt = record.occurredAt();
            entry.metadata = record.metadata();
            return entry;
        };
        return new OutcomeRecordSaveCore(repo, props.metadata(), factory);
    }

    @Bean
    OutcomeRecorderCore outcomeRecorderCore(OutcomeRecordSaveCore saveCore,
                                            LedgerProperties props,
                                            CurrentPrincipal principal) {
        return new OutcomeRecorderCore(saveCore, props.outcome(), principal::tenancyId);
    }

    // --- Privacy defaults ---

    @Bean
    @ConditionalOnMissingBean(ActorIdentityProvider.class)
    ActorIdentityProvider passThroughActorIdentityProvider() {
        return new PassThroughActorIdentityProvider();
    }

    @Bean
    @ConditionalOnMissingBean(ContentSanitiser.class)
    ContentSanitiser passThroughContentSanitiser() {
        return new PassThroughContentSanitiser();
    }
}

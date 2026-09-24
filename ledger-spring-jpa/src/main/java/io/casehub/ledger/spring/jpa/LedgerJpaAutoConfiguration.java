package io.casehub.ledger.spring.jpa;

import io.casehub.ledger.api.spi.ActorIdentityProvider;
import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import io.casehub.ledger.api.spi.CrossTenantLedgerEntryRepository;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.spi.LedgerMerkleFrontierRepository;
import io.casehub.ledger.api.spi.TrustScoreSnapshotRepository;
import io.casehub.ledger.core.config.LedgerProperties;
import io.casehub.ledger.core.enricher.EnricherPipelineCore;
import io.casehub.ledger.core.model.AttestationRecordedEvent;
import io.casehub.ledger.core.privacy.ContentSanitiser;
import io.casehub.ledger.core.service.MerklePublisherCore;
import io.casehub.ledger.core.signing.AgentEntrySigner;
import io.casehub.ledger.core.signing.AgentSigner;
import io.casehub.ledger.jpa.LedgerSequenceAllocator;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnBean(EntityManager.class)
public class LedgerJpaAutoConfiguration {

    @Bean
    LedgerSequenceAllocator ledgerSequenceAllocator(EntityManager em) {
        return new LedgerSequenceAllocator(em);
    }

    @Bean
    @ConditionalOnMissingBean(LedgerEntryRepository.class)
    SpringJpaLedgerEntryRepository springJpaLedgerEntryRepository(
            EntityManager em,
            LedgerProperties properties,
            LedgerMerkleFrontierRepository frontierRepo,
            ActorIdentityProvider actorIdentityProvider,
            ContentSanitiser contentSanitiser,
            LedgerSequenceAllocator sequenceAllocator,
            EnricherPipelineCore enricherPipeline,
            ObjectProvider<AgentEntrySigner> agentEntrySigner,
            MerklePublisherCore merklePublisher,
            ApplicationEventPublisher eventPublisher) {
        return new SpringJpaLedgerEntryRepository(
                em, properties, frontierRepo, actorIdentityProvider,
                contentSanitiser, sequenceAllocator, enricherPipeline,
                agentEntrySigner.getIfAvailable(() -> noOpEntrySigner()),
                merklePublisher,
                event -> eventPublisher.publishEvent(event));
    }

    @Bean
    @ConditionalOnMissingBean(CrossTenantLedgerEntryRepository.class)
    SpringJpaCrossTenantLedgerEntryRepository springJpaCrossTenantLedgerEntryRepository(
            EntityManager em,
            ActorIdentityProvider actorIdentityProvider) {
        return new SpringJpaCrossTenantLedgerEntryRepository(em, actorIdentityProvider);
    }

    @Bean
    @ConditionalOnMissingBean(ActorTrustScoreRepository.class)
    SpringJpaActorTrustScoreRepository springJpaActorTrustScoreRepository(EntityManager em) {
        return new SpringJpaActorTrustScoreRepository(em);
    }

    @Bean
    @ConditionalOnMissingBean(TrustScoreSnapshotRepository.class)
    SpringJpaTrustScoreSnapshotRepository springJpaTrustScoreSnapshotRepository(EntityManager em) {
        return new SpringJpaTrustScoreSnapshotRepository(em);
    }

    @Bean
    @ConditionalOnMissingBean(LedgerMerkleFrontierRepository.class)
    SpringJpaLedgerMerkleFrontierRepository springJpaLedgerMerkleFrontierRepository(EntityManager em) {
        return new SpringJpaLedgerMerkleFrontierRepository(em);
    }

    private static AgentEntrySigner noOpEntrySigner() {
        AgentSigner noOp = (actorId, data) -> java.util.Optional.empty();
        return new AgentEntrySigner(noOp);
    }
}

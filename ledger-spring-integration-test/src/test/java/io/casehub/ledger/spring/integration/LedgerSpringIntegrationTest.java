package io.casehub.ledger.spring.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.core.config.LedgerProperties;
import io.casehub.ledger.core.enricher.EnricherPipelineCore;
import io.casehub.ledger.core.service.LedgerAppenderCore;
import io.casehub.ledger.core.service.VerificationServiceCore;
import io.casehub.ledger.jpa.PlainLedgerEntry;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.CurrentPrincipal;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class LedgerSpringIntegrationTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", pg::getJdbcUrl);
        registry.add("spring.datasource.username", pg::getUsername);
        registry.add("spring.datasource.password", pg::getPassword);
    }

    @Autowired
    private ApplicationContext context;

    @LocalServerPort
    private int port;

    @Test
    void contextLoads() {
    }

    @Test
    void coreServiceBeansInjectable() {
        assertThat(context.getBean(LedgerProperties.class)).isNotNull();
        assertThat(context.getBean(EnricherPipelineCore.class)).isNotNull();
        assertThat(context.getBean(LedgerAppenderCore.class)).isNotNull();
        assertThat(context.getBean(VerificationServiceCore.class)).isNotNull();
        assertThat(context.getBean(CurrentPrincipal.class)).isNotNull();
    }

    @Test
    void jpaRepositoryPersistsAndFinds() {
        var repo = context.getBean(LedgerEntryRepository.class);

        var subjectId = UUID.randomUUID();
        var entry = new PlainLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.actorId = "test-actor";
        entry.actorType = ActorType.HUMAN;
        entry.subjectId = subjectId;
        entry.entryType = LedgerEntryType.EVENT;
        entry.occurredAt = Instant.now();

        repo.save(entry, "test-tenant");

        var found = repo.findBySubjectId(subjectId, "test-tenant");
        assertThat(found).isNotEmpty();
        assertThat(found.getFirst().actorId).isEqualTo("test-actor");
    }

    @Test
    void healthCheckReturnsUp() throws Exception {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/actuator/health"))
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }

    @Test
    void flywayMigrationsRunSuccessfully() {
        var flyway = context.getBean(org.flywaydb.core.Flyway.class);
        var info = flyway.info();
        assertThat(info.applied()).isNotEmpty();
        assertThat(info.pending()).isEmpty();
    }

    @Test
    void ledgerPropertiesHaveCorrectDefaults() {
        var props = context.getBean(LedgerProperties.class);
        assertThat(props.enabled()).isTrue();
        assertThat(props.hashChain().enabled()).isTrue();
        assertThat(props.trustScore().enabled()).isFalse();
    }
}

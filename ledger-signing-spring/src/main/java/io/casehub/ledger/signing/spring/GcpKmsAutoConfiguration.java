package io.casehub.ledger.signing.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.casehub.ledger.core.signing.AgentSigner;
import io.casehub.ledger.signing.gcp.GcpKmsSigningClient;

@AutoConfiguration
@ConditionalOnClass(GcpKmsSigningClient.class)
@EnableConfigurationProperties(GcpKmsSpringProperties.class)
@EnableScheduling
public class GcpKmsAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "casehub.ledger.gcp-kms", name = "key-mapping")
    AgentSigner gcpKmsAgentSigner(final GcpKmsSpringProperties props) {
        return new GcpKmsSpringAgentSigner(props.getKeyMapping());
    }
}

package io.casehub.ledger.signing.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.casehub.ledger.core.signing.AgentSigner;
import io.casehub.ledger.signing.vault.VaultTransitSigningClient;

@AutoConfiguration
@ConditionalOnClass(VaultTransitSigningClient.class)
@EnableConfigurationProperties(VaultTransitSpringProperties.class)
@EnableScheduling
public class VaultTransitAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "casehub.ledger.vault-transit", name = "address")
    AgentSigner vaultTransitAgentSigner(final VaultTransitSpringProperties props) {
        return new VaultTransitSpringAgentSigner(props.toSigningConfig(), props.toAuthConfig());
    }
}

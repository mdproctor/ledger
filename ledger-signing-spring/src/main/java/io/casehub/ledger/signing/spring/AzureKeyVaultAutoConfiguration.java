package io.casehub.ledger.signing.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.casehub.ledger.core.signing.AgentSigner;
import io.casehub.ledger.signing.azure.AzureKeyVaultSigningClient;

@AutoConfiguration
@ConditionalOnClass(AzureKeyVaultSigningClient.class)
@EnableConfigurationProperties(AzureKeyVaultSpringProperties.class)
@EnableScheduling
public class AzureKeyVaultAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "casehub.ledger.azure-keyvault", name = "key-mapping")
    AgentSigner azureKeyVaultAgentSigner(final AzureKeyVaultSpringProperties props) {
        return new AzureKeyVaultSpringAgentSigner(props.getKeyMapping());
    }
}

package io.casehub.ledger.signing.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.casehub.ledger.core.signing.AgentSigner;
import io.casehub.ledger.signing.aws.AwsKmsSigningClient;

@AutoConfiguration
@ConditionalOnClass(AwsKmsSigningClient.class)
@EnableConfigurationProperties(AwsKmsSpringProperties.class)
@EnableScheduling
public class AwsKmsAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "casehub.ledger.aws-kms", name = "region")
    AgentSigner awsKmsAgentSigner(final AwsKmsSpringProperties props) {
        return new AwsKmsSpringAgentSigner(props.toSigningConfig());
    }
}

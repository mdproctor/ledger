package io.casehub.ledger.signing.spring;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.casehub.ledger.signing.aws.AwsKmsSigningConfig;

@ConfigurationProperties(prefix = "casehub.ledger.aws-kms")
public class AwsKmsSpringProperties {

    private String region = "us-east-1";
    private Map<String, String> keyMapping = Map.of();
    private long refreshIntervalMs = 300000;

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public Map<String, String> getKeyMapping() { return keyMapping; }
    public void setKeyMapping(Map<String, String> keyMapping) { this.keyMapping = keyMapping; }

    public long getRefreshIntervalMs() { return refreshIntervalMs; }
    public void setRefreshIntervalMs(long refreshIntervalMs) { this.refreshIntervalMs = refreshIntervalMs; }

    public AwsKmsSigningConfig toSigningConfig() {
        return new AwsKmsSigningConfig(region, keyMapping);
    }
}

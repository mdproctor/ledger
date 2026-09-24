package io.casehub.ledger.signing.spring;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "casehub.ledger.gcp-kms")
public class GcpKmsSpringProperties {

    private Map<String, String> keyMapping = Map.of();
    private long refreshIntervalMs = 300000;

    public Map<String, String> getKeyMapping() { return keyMapping; }
    public void setKeyMapping(Map<String, String> keyMapping) { this.keyMapping = keyMapping; }

    public long getRefreshIntervalMs() { return refreshIntervalMs; }
    public void setRefreshIntervalMs(long refreshIntervalMs) { this.refreshIntervalMs = refreshIntervalMs; }
}

package io.casehub.ledger.signing.spring;

import java.util.Map;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.casehub.ledger.signing.vault.VaultTransitAuthConfig;
import io.casehub.ledger.signing.vault.VaultTransitSigningConfig;

@ConfigurationProperties(prefix = "casehub.ledger.vault-transit")
public class VaultTransitSpringProperties {

    private String address = "http://localhost:8200";
    private Map<String, String> keyMapping = Map.of();
    private long refreshIntervalMs = 300000;
    private Auth auth = new Auth();

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Map<String, String> getKeyMapping() { return keyMapping; }
    public void setKeyMapping(Map<String, String> keyMapping) { this.keyMapping = keyMapping; }

    public long getRefreshIntervalMs() { return refreshIntervalMs; }
    public void setRefreshIntervalMs(long refreshIntervalMs) { this.refreshIntervalMs = refreshIntervalMs; }

    public Auth getAuth() { return auth; }
    public void setAuth(Auth auth) { this.auth = auth; }

    public VaultTransitSigningConfig toSigningConfig() {
        return new VaultTransitSigningConfig(address, keyMapping);
    }

    public VaultTransitAuthConfig toAuthConfig() {
        return new VaultTransitAuthConfig(
                mapMethod(auth.method),
                Optional.ofNullable(auth.token),
                Optional.ofNullable(auth.roleId),
                Optional.ofNullable(auth.secretId),
                Optional.ofNullable(auth.role),
                Optional.ofNullable(auth.jwtPath),
                Optional.ofNullable(auth.jwt),
                Optional.ofNullable(auth.mountPath));
    }

    private static VaultTransitAuthConfig.AuthMethod mapMethod(String method) {
        return VaultTransitAuthConfig.AuthMethod.valueOf(method.toUpperCase());
    }

    public static class Auth {
        private String method = "token";
        private String token;
        private String roleId;
        private String secretId;
        private String role;
        private String jwtPath;
        private String jwt;
        private String mountPath;

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getRoleId() { return roleId; }
        public void setRoleId(String roleId) { this.roleId = roleId; }
        public String getSecretId() { return secretId; }
        public void setSecretId(String secretId) { this.secretId = secretId; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getJwtPath() { return jwtPath; }
        public void setJwtPath(String jwtPath) { this.jwtPath = jwtPath; }
        public String getJwt() { return jwt; }
        public void setJwt(String jwt) { this.jwt = jwt; }
        public String getMountPath() { return mountPath; }
        public void setMountPath(String mountPath) { this.mountPath = mountPath; }
    }
}

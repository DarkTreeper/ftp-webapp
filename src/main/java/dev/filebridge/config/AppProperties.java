package dev.filebridge.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "filebridge")
public class AppProperties {
    private Path localRoot = Path.of("/data/local");
    private Path demoRemoteRoot = Path.of("demo-remote-data");
    private String remoteRoot = "/";
    private boolean demoMode = false;
    private long maxUploadBytes = 1_073_741_824L;
    private final Ftp ftp = new Ftp();
    private final Security security = new Security();

    public Path getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(Path localRoot) {
        this.localRoot = localRoot;
    }

    public Path getDemoRemoteRoot() {
        return demoRemoteRoot;
    }

    public void setDemoRemoteRoot(Path demoRemoteRoot) {
        this.demoRemoteRoot = demoRemoteRoot;
    }

    public String getRemoteRoot() {
        return remoteRoot;
    }

    public void setRemoteRoot(String remoteRoot) {
        this.remoteRoot = remoteRoot;
    }

    public boolean isDemoMode() {
        return demoMode;
    }

    public void setDemoMode(boolean demoMode) {
        this.demoMode = demoMode;
    }

    public long getMaxUploadBytes() {
        return maxUploadBytes;
    }

    public void setMaxUploadBytes(long maxUploadBytes) {
        this.maxUploadBytes = maxUploadBytes;
    }

    public Ftp getFtp() {
        return ftp;
    }

    public Security getSecurity() {
        return security;
    }

    public static class Ftp {
        private String host = "172.16.120.41";
        private int port = 21;
        private String username;
        private String password;
        private boolean ftps = false;
        private boolean passiveMode = true;
        private int connectTimeoutMillis = 10_000;
        private int dataTimeoutMillis = 30_000;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isFtps() {
            return ftps;
        }

        public void setFtps(boolean ftps) {
            this.ftps = ftps;
        }

        public boolean isPassiveMode() {
            return passiveMode;
        }

        public void setPassiveMode(boolean passiveMode) {
            this.passiveMode = passiveMode;
        }

        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public void setConnectTimeoutMillis(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }

        public int getDataTimeoutMillis() {
            return dataTimeoutMillis;
        }

        public void setDataTimeoutMillis(int dataTimeoutMillis) {
            this.dataTimeoutMillis = dataTimeoutMillis;
        }
    }

    public static class Security {
        private String username;
        private String password;
        private String passwordHash;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getPasswordHash() {
            return passwordHash;
        }

        public void setPasswordHash(String passwordHash) {
            this.passwordHash = passwordHash;
        }
    }
}

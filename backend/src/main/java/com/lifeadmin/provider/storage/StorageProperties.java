package com.lifeadmin.provider.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code lifeadmin.storage.*}. Endpoint/keys/bucket target an S3-compatible store (MinIO
 * locally; AWS S3 / Cloudflare R2 / GCS in production). {@code pathStyleAccess} is required for
 * MinIO. {@code presignTtlSeconds} bounds download-URL lifetime (G10).
 */
@ConfigurationProperties(prefix = "lifeadmin.storage")
public class StorageProperties {

    private String endpoint;
    private String region = "us-east-1";
    private String accessKey;
    private String secretKey;
    private String bucket = "lifeadmin-documents";
    private boolean pathStyleAccess = true;
    private long presignTtlSeconds = 300;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public boolean isPathStyleAccess() { return pathStyleAccess; }
    public void setPathStyleAccess(boolean pathStyleAccess) { this.pathStyleAccess = pathStyleAccess; }

    public long getPresignTtlSeconds() { return presignTtlSeconds; }
    public void setPresignTtlSeconds(long presignTtlSeconds) { this.presignTtlSeconds = presignTtlSeconds; }
}

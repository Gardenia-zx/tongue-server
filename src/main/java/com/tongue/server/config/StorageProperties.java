package com.tongue.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tongue.storage")
public class StorageProperties {

    private String mode = "local";
    private String publicBaseUrl = "";
    private String uploadRoot = "D:/tongue/storage/uploads";
    private String reportRoot = "D:/tongue/storage/reports";
    private long maxImageSizeBytes = 10L * 1024L * 1024L;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getUploadRoot() {
        return uploadRoot;
    }

    public void setUploadRoot(String uploadRoot) {
        this.uploadRoot = uploadRoot;
    }

    public String getReportRoot() {
        return reportRoot;
    }

    public void setReportRoot(String reportRoot) {
        this.reportRoot = reportRoot;
    }

    public long getMaxImageSizeBytes() {
        return maxImageSizeBytes;
    }

    public void setMaxImageSizeBytes(long maxImageSizeBytes) {
        this.maxImageSizeBytes = maxImageSizeBytes;
    }
}

package com.tongue.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tongue.agent")
public class AgentProperties {

    private String baseUrl = "http://127.0.0.1:8000";
    private String runPath = "/api/v1/agent/run";
    private String ackPath = "/api/v1/agent/turns/ack";
    private int connectTimeoutMillis = 10000;
    private int readTimeoutMillis = 240000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getRunPath() {
        return runPath;
    }

    public void setRunPath(String runPath) {
        this.runPath = runPath;
    }

    public String getAckPath() {
        return ackPath;
    }

    public void setAckPath(String ackPath) {
        this.ackPath = ackPath;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }
}

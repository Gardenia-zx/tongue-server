package com.tongue.server.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SmsSendResponse {
    @JsonProperty("ttl_seconds")
    public long ttlSeconds;
    @JsonProperty("dev_code")
    public String devCode;
}

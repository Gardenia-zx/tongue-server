package com.tongue.server.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AuthLoginResponse {
    @JsonProperty("access_token")
    public String accessToken;
    @JsonProperty("token_type")
    public String tokenType = "Bearer";
    @JsonProperty("expires_at")
    public long expiresAt;
    public UserMeResponse user;
}

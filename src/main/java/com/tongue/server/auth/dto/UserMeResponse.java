package com.tongue.server.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserMeResponse {
    @JsonProperty("user_id")
    public Long userId;
    public String phone;
    public String role;
    public String nickname;
    public String gender;
    public Integer age;
    @JsonProperty("avatar_file_id")
    public Long avatarFileId;
    @JsonProperty("health_focus")
    public String healthFocus;
}

package com.tongue.server.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserProfileUpdateRequest {
    public String nickname;
    public String gender;
    public Integer age;
    @JsonProperty("avatar_file_id")
    public Long avatarFileId;
    @JsonProperty("health_focus")
    public String healthFocus;
}

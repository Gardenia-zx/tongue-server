package com.tongue.server.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DoctorProfileUpsertRequest {
    @JsonProperty("real_name")
    public String realName;
    public String title;
    public String introduction;
    public String specialty;
}

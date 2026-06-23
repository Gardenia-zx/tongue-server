package com.tongue.server.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DoctorProfileResponse {
    @JsonProperty("doctor_id")
    public Long doctorId;
    @JsonProperty("user_id")
    public Long userId;
    @JsonProperty("real_name")
    public String realName;
    public String title;
    public String introduction;
    public String specialty;
    @JsonProperty("review_status")
    public String reviewStatus;
}

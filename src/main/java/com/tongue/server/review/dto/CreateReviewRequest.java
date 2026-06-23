package com.tongue.server.review.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;

public class CreateReviewRequest {
    @NotNull
    @JsonProperty("report_id")
    public Long reportId;
    @JsonProperty("user_remark")
    public String userRemark;
}

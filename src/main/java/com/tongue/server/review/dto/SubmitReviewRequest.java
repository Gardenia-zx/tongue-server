package com.tongue.server.review.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SubmitReviewRequest {
    @JsonProperty("comment_text")
    public String commentText;
    @JsonProperty("revised_report")
    public Object revisedReport;
}

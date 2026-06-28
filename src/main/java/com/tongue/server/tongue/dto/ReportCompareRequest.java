package com.tongue.server.tongue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReportCompareRequest {
    @JsonProperty("base_report_id")
    public Long baseReportId;
    @JsonProperty("target_report_id")
    public Long targetReportId;
}

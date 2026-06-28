package com.tongue.server.tongue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class ReportCompareResponse {
    @JsonProperty("base_report_id")
    public Long baseReportId;
    @JsonProperty("target_report_id")
    public Long targetReportId;
    public List<FeatureDiffItem> added = new ArrayList<FeatureDiffItem>();
    public List<FeatureDiffItem> removed = new ArrayList<FeatureDiffItem>();
    public List<FeatureDiffItem> persistent = new ArrayList<FeatureDiffItem>();
    public List<FeatureDiffItem> changed = new ArrayList<FeatureDiffItem>();
    public List<FeatureDiffItem> unsupported = new ArrayList<FeatureDiffItem>();
    public String explanation;
    @JsonProperty("observation_suggestions")
    public List<String> observationSuggestions = new ArrayList<String>();
    @JsonProperty("agent_status")
    public String agentStatus;

    public static class FeatureDiffItem {
        @JsonProperty("feature_code")
        public String featureCode;
        @JsonProperty("base_confidence")
        public Double baseConfidence;
        @JsonProperty("target_confidence")
        public Double targetConfidence;
        @JsonProperty("change_type")
        public String changeType;
    }
}

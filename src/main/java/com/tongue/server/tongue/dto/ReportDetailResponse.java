package com.tongue.server.tongue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class ReportDetailResponse {
    @JsonProperty("report_id")
    public Long reportId;
    @JsonProperty("task_id")
    public Long taskId;
    @JsonProperty("image_file_id")
    public Long imageFileId;
    @JsonProperty("thread_id")
    public String threadId;
    @JsonProperty("report_status")
    public String reportStatus;
    public String summary;
    @JsonProperty("feature_summary")
    public String featureSummary;
    @JsonProperty("detected_feature_codes")
    public Object detectedFeatureCodes;
    @JsonProperty("standard_features")
    public Object standardFeatures;
    @JsonProperty("rag_query")
    public String ragQuery;
    @JsonProperty("rag_grounded")
    public Boolean ragGrounded;
    @JsonProperty("rag_evidence")
    public Object ragEvidence;
    @JsonProperty("draft_report")
    public Object draftReport;
    @JsonProperty("risk_disclaimer")
    public String riskDisclaimer;
    @JsonProperty("structured_report")
    public Object structuredReport;
    @JsonProperty("state_snapshot")
    public UserStateSnapshotResponse stateSnapshot;
    @JsonProperty("analysis_quality_score")
    public Double analysisQualityScore;
    @JsonProperty("analysis_quality_level")
    public String analysisQualityLevel;
    @JsonProperty("quality_metrics")
    public Map<String, Object> qualityMetrics;
    @JsonProperty("quality_version")
    public String qualityVersion;
    @JsonProperty("analysis_quality_version")
    public String analysisQualityVersion;
}

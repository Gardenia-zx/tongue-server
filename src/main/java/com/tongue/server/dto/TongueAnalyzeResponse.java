package com.tongue.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class TongueAnalyzeResponse {

    @JsonProperty("report_id")
    private Long reportId;
    @JsonProperty("task_id")
    private Long taskId;
    @JsonProperty("thread_id")
    private String threadId;
    private String status;
    private String summary;
    @JsonProperty("detected_feature_codes")
    private List<String> detectedFeatureCodes;
    @JsonProperty("rag_query")
    private String ragQuery;
    @JsonProperty("draft_report")
    private Object draftReport;

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getDetectedFeatureCodes() {
        return detectedFeatureCodes;
    }

    public void setDetectedFeatureCodes(List<String> detectedFeatureCodes) {
        this.detectedFeatureCodes = detectedFeatureCodes;
    }

    public String getRagQuery() {
        return ragQuery;
    }

    public void setRagQuery(String ragQuery) {
        this.ragQuery = ragQuery;
    }

    public Object getDraftReport() {
        return draftReport;
    }

    public void setDraftReport(Object draftReport) {
        this.draftReport = draftReport;
    }
}

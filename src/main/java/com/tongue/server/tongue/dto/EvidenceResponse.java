package com.tongue.server.tongue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EvidenceResponse {
    @JsonProperty("evidence_id")
    public Long evidenceId;
    @JsonProperty("chunk_id")
    public String chunkId;
    @JsonProperty("doc_id")
    public String docId;
    public String title;
    public String content;
    @JsonProperty("source_uri")
    public String sourceUri;
    @JsonProperty("final_score")
    public Double finalScore;
}

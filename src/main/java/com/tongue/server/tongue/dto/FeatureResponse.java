package com.tongue.server.tongue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FeatureResponse {
    @JsonProperty("feature_id")
    public Long featureId;
    @JsonProperty("feature_code")
    public String featureCode;
    @JsonProperty("feature_group")
    public String featureGroup;
    public Double confidence;
}

package com.tongue.server.storage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FileUploadResponse {
    @JsonProperty("file_id")
    public Long fileId;
    @JsonProperty("view_url")
    public String viewUrl;
    @JsonProperty("content_type")
    public String contentType;
    @JsonProperty("file_size")
    public Long fileSize;
    @JsonProperty("original_filename")
    public String originalFilename;
}

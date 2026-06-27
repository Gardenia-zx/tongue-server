package com.tongue.server.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AgentConversationResponse {

    @JsonProperty("conversation_id")
    public String conversationId;

    @JsonProperty("thread_id")
    public String threadId;

    @JsonProperty("active_report_id")
    public Long activeReportId;

    @JsonProperty("last_image_file_id")
    public Long lastImageFileId;

    @JsonProperty("latest_report")
    public Map<String, Object> latestReport;

    public List<Message> messages = new ArrayList<Message>();

    public static class Message {
        @JsonProperty("message_id")
        public Long messageId;

        public String role;

        public String content;

        @JsonProperty("structured_content")
        public Map<String, Object> structuredContent;

        @JsonProperty("content_type")
        public String contentType;

        @JsonProperty("image_file_id")
        public Long imageFileId;

        @JsonProperty("report_id")
        public Long reportId;

        @JsonProperty("created_at")
        public String createdAt;
    }
}

package com.tongue.server.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public class AgentChatRequest {

    @NotBlank
    public String message;

    @JsonProperty("thread_id")
    public String threadId;

    @JsonProperty("conversation_id")
    public String conversationId;

    @JsonProperty("client_request_id")
    public String clientRequestId;

    @JsonProperty("recent_messages")
    public List<RecentMessage> recentMessages;

    @JsonProperty("latest_report")
    public Map<String, Object> latestReport;

    public static class RecentMessage {
        public String role;
        public String content;

        @JsonProperty("report_id")
        public Long reportId;
    }
}

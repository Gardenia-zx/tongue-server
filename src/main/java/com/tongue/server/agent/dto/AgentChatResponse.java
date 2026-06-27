package com.tongue.server.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class AgentChatResponse {

    public String status;

    @JsonProperty("thread_id")
    public String threadId;

    @JsonProperty("conversation_id")
    public String conversationId;

    public String content;

    @JsonProperty("structured_content")
    public Map<String, Object> structuredContent;

    @JsonProperty("intent_result")
    public Map<String, Object> intentResult;

    @JsonProperty("next_action")
    public Map<String, Object> nextAction;
}

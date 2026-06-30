package com.tongue.server.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongue.server.agent.service.AgentChatTurnStore.AgentChatConflictException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class AgentResponseSanitizer {

    private static final List<String> INTERNAL_MARKERS = Arrays.asList(
            "\"structured_content\"",
            "\"structuredContent\"",
            "\"tool_decision\"",
            "\"tool_calls\"",
            "\"quality_review\"",
            "\"next_action\"",
            "\"state_snapshot\"",
            "\"agent_loop\"",
            "\"final_answer\""
    );

    private final ObjectMapper objectMapper;

    public AgentResponseSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SanitizedAgentMessage sanitize(JsonNode sourceMessage) {
        String content = sourceMessage.path("content").asText("").trim();
        JsonNode structuredContent = sourceMessage.get("structured_content");
        if (structuredContent == null) {
            structuredContent = sourceMessage.get("structuredContent");
        }

        if (looksLikeJson(content)) {
            JsonNode embedded = parseEmbeddedJson(content);
            if (embedded != null && embedded.isObject()) {
                if (embedded.hasNonNull("content")) {
                    content = embedded.get("content").asText("").trim();
                }
                if (structuredContent == null) {
                    structuredContent = embedded.get("structured_content");
                    if (structuredContent == null) {
                        structuredContent = embedded.get("structuredContent");
                    }
                }
            }
        }

        if (content.isEmpty()) {
            throw new AgentChatConflictException("INVALID_AGENT_RESPONSE", "Agent 返回了空消息");
        }
        if (looksLikeInternalJson(content)) {
            throw new AgentChatConflictException("RAW_AGENT_JSON_BLOCKED", "Agent 返回了内部 JSON，已阻止直接展示");
        }

        boolean structuredInvalid = structuredContent != null && !isValidStructuredContent(structuredContent);
        return new SanitizedAgentMessage(content, structuredInvalid ? null : structuredContent, structuredInvalid);
    }

    private boolean looksLikeJson(String content) {
        String value = content.trim();
        return value.startsWith("{") || value.startsWith("```json") || value.startsWith("```");
    }

    private boolean looksLikeInternalJson(String content) {
        String value = content.trim();
        if (!value.startsWith("{") && !value.startsWith("```json") && !value.startsWith("```")
                && !value.contains("```json")) {
            return false;
        }
        for (String marker : INTERNAL_MARKERS) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private JsonNode parseEmbeddedJson(String content) {
        try {
            String normalized = content
                    .replaceFirst("^```json\\s*", "")
                    .replaceFirst("^```\\s*", "")
                    .replaceFirst("\\s*```$", "");
            return objectMapper.readTree(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isValidStructuredContent(JsonNode value) {
        if (value == null || value.isNull()) {
            return true;
        }
        if (!value.isObject()) {
            return false;
        }
        JsonNode sections = value.get("sections");
        if (sections != null && !sections.isNull()) {
            if (!sections.isArray()) {
                return false;
            }
            for (JsonNode section : sections) {
                if (!section.isObject()) {
                    return false;
                }
                JsonNode items = section.get("items");
                if (items != null && !items.isNull()) {
                    if (!items.isArray()) {
                        return false;
                    }
                    for (JsonNode item : items) {
                        if (!item.isTextual()) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    public static class SanitizedAgentMessage {
        private final String content;
        private final JsonNode structuredContent;
        private final boolean structuredContentInvalid;

        SanitizedAgentMessage(String content, JsonNode structuredContent, boolean structuredContentInvalid) {
            this.content = content;
            this.structuredContent = structuredContent;
            this.structuredContentInvalid = structuredContentInvalid;
        }

        public String getContent() { return content; }
        public JsonNode getStructuredContent() { return structuredContent; }
        public boolean isStructuredContentInvalid() { return structuredContentInvalid; }
    }
}

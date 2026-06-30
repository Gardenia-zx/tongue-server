package com.tongue.server.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongue.server.agent.service.AgentChatTurnStore.AgentChatConflictException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentResponseSanitizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentResponseSanitizer sanitizer = new AgentResponseSanitizer(objectMapper);

    @Test
    void passesNaturalTextAndStructuredContent() throws Exception {
        JsonNode source = objectMapper.readTree("{\"content\":\"这是自然语言回答。\",\"structured_content\":{\"sections\":[{\"title\":\"建议\",\"items\":[\"规律作息\"]}]}}");

        AgentResponseSanitizer.SanitizedAgentMessage result = sanitizer.sanitize(source);

        assertEquals("这是自然语言回答。", result.getContent());
        assertEquals("建议", result.getStructuredContent().path("sections").get(0).path("title").asText());
    }

    @Test
    void unwrapsEmbeddedFinalAnswerJson() throws Exception {
        JsonNode source = objectMapper.createObjectNode()
                .put("content", "{\"type\":\"final_answer\",\"content\":\"可以继续观察。\",\"structured_content\":{\"title\":\"观察\"}}");

        AgentResponseSanitizer.SanitizedAgentMessage result = sanitizer.sanitize(source);

        assertEquals("可以继续观察。", result.getContent());
        assertEquals("观察", result.getStructuredContent().path("title").asText());
    }

    @Test
    void rejectsInternalJsonContent() throws Exception {
        JsonNode source = objectMapper.createObjectNode()
                .put("content", "{\"type\":\"final_answer\",\"structured_content\":{}");

        assertThrows(AgentChatConflictException.class, () -> sanitizer.sanitize(source));
    }

    @Test
    void rejectsPrefixedFencedInternalJsonContent() throws Exception {
        JsonNode source = objectMapper.createObjectNode()
                .put("content", "工具结果足够。\n```json\n{\"type\":\"final_answer\",\"structured_content\":{}}\n```");

        assertThrows(AgentChatConflictException.class, () -> sanitizer.sanitize(source));
    }

    @Test
    void dropsInvalidStructuredContentButKeepsText() throws Exception {
        JsonNode source = objectMapper.readTree("{\"content\":\"自然语言可以展示。\",\"structured_content\":{\"sections\":[{\"title\":\"建议\",\"items\":[{\"x\":1}]}]}}");

        AgentResponseSanitizer.SanitizedAgentMessage result = sanitizer.sanitize(source);

        assertEquals("自然语言可以展示。", result.getContent());
        assertNull(result.getStructuredContent());
        assertTrue(result.isStructuredContentInvalid());
    }
}

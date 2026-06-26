package com.tongue.server.agentchat.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentChatV2ContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void deserializesSnakeCaseRequestContract() throws Exception {
        String json = "{" +
                "\"request_id\":\"req-1\"," +
                "\"client_message_id\":\"msg-user-1\"," +
                "\"thread_id\":\"thread-1\"," +
                "\"conversation_id\":\"conversation-1\"," +
                "\"message\":{" +
                "\"role\":\"user\"," +
                "\"content_type\":\"text\"," +
                "\"content\":\"我最近食欲不好\"}," +
                "\"context_binding\":{" +
                "\"mode\":\"NONE\"," +
                "\"report_id\":null}," +
                "\"client_context\":{" +
                "\"page\":\"analysis\"}" +
                "}";

        AgentChatV2Request request = objectMapper.readValue(json, AgentChatV2Request.class);

        assertEquals("req-1", request.getRequestId());
        assertEquals("msg-user-1", request.getClientMessageId());
        assertEquals("thread-1", request.getThreadId());
        assertEquals("conversation-1", request.getConversationId());
        assertEquals("我最近食欲不好", request.getMessage().getContent());
        assertEquals("NONE", request.getContextBinding().getMode());
    }

    @Test
    void serializesTurnOwnershipAndAssistantReportReference() throws Exception {
        AgentChatV2Response response = new AgentChatV2Response();
        response.setStatus("COMPLETED");
        response.setRequestId("req-1");
        response.setTurnId("turn-1");
        response.setThreadId("thread-1");
        response.setConversationId("conversation-1");
        response.setTraceId("trace-1");

        AgentChatV2Response.AssistantMessage message = new AgentChatV2Response.AssistantMessage();
        message.setMessageId("msg-assistant-1");
        message.setContentType("text");
        message.setContent("本轮回答");
        message.setReportRef(new AgentChatV2Response.ReportRef(1001L, "REFERENCED"));
        message.setCreatedAt(LocalDateTime.of(2026, 6, 26, 17, 0));
        response.setAssistantMessage(message);

        JsonNode json = objectMapper.valueToTree(response);

        assertEquals("req-1", json.path("request_id").asText());
        assertEquals("turn-1", json.path("turn_id").asText());
        assertEquals("msg-assistant-1", json.path("assistant_message").path("message_id").asText());
        assertEquals(1001L, json.path("assistant_message").path("report_ref").path("report_id").asLong());
        assertNotNull(json.path("assistant_message").path("created_at"));
    }
}

package com.tongue.server.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tongue.server.config.AgentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentGatewayClientV2Test {

    private final AgentGatewayClientV2 client = new AgentGatewayClientV2(
            new AgentProperties(),
            new ObjectMapper(),
            new RestTemplateBuilder()
    );

    @Test
    void statefulPayloadUsesNestedOptionsWithoutContextBundle() {
        ObjectNode payload = client.buildPayload(invocation());

        assertFalse(payload.has("context_bundle"));
        assertEquals("stateful", payload.path("options").path("context").path("mode").asText());
        assertTrue(payload.path("options").path("memory").path("can_read").asBoolean());
        assertTrue(payload.path("options").path("memory").path("can_write").asBoolean());
        assertFalse(payload.path("options").has("memory.can_read"));
    }

    @Test
    void statefulReportPayloadIncludesOnlyReportRefContextBundle() {
        AgentGatewayClientV2.Invocation invocation = invocation();
        ObjectNode reportRef = new ObjectMapper().createObjectNode();
        reportRef.put("report_id", 9L);
        reportRef.put("trusted", true);
        ObjectNode loadedSections = new ObjectMapper().createObjectNode();
        loadedSections.put("status", "OK");
        loadedSections.put("report_id", 9L);
        invocation.setReportId(9L);
        invocation.setReportContextMode("ACTIVE_REPORT");
        invocation.setActiveReportRef(reportRef);
        invocation.setLoadedReportSections(loadedSections);

        ObjectNode payload = client.buildPayload(invocation);

        assertEquals(9L, payload.path("context_bundle").path("active_report_ref").path("report_id").asLong());
        assertEquals(9L, payload.path("context_bundle").path("loaded_report_sections").path("report_id").asLong());
        assertFalse(payload.path("context_bundle").has("recent_messages"));
        assertFalse(payload.path("context_bundle").has("conversation_summary"));
    }

    @Test
    void mysqlRecoveryPayloadIncludesContextBundle() {
        AgentGatewayClientV2.Invocation invocation = invocation();
        invocation.setContextMode("mysql_recovery");

        ObjectNode payload = client.buildPayload(invocation);

        assertEquals("mysql_recovery", payload.path("options").path("context").path("mode").asText());
        assertEquals("mysql_recovery", payload.path("context_bundle").path("mode").asText());
        assertTrue(payload.path("context_bundle").path("recent_messages").isArray());
    }

    private AgentGatewayClientV2.Invocation invocation() {
        AgentGatewayClientV2.Invocation invocation = new AgentGatewayClientV2.Invocation();
        invocation.setUserId(1L);
        invocation.setRequestId("req-1");
        invocation.setTraceId("trace-1");
        invocation.setTurnId("turn-1");
        invocation.setThreadId("thread-1");
        invocation.setThreadEpoch(1);
        invocation.setConversationId("conversation-1");
        invocation.setUserMessageId("user-1");
        invocation.setAssistantMessageId("assistant-1");
        invocation.setContent("你好");
        invocation.setRecentMessages(Collections.emptyList());
        invocation.setClientContext(Collections.<String, Object>emptyMap());
        return invocation;
    }
}

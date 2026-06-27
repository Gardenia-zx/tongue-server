package com.tongue.server.agent.controller;

import com.tongue.server.agent.dto.AgentChatV2Request;
import com.tongue.server.agent.dto.AgentChatV2Response;
import com.tongue.server.agent.service.AgentChatV2Service;
import com.tongue.server.agent.service.AgentChatTurnStore.AgentChatConflictException;
import com.tongue.server.agent.service.AgentGatewayClientV2.AgentGatewayException;
import com.tongue.server.auth.AuthContext;
import com.tongue.server.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class AgentChatV2Controller {

    private final AgentChatV2Service service;

    @Value("${JAVA_INTERNAL_API_KEY:}")
    private String internalApiKey;

    public AgentChatV2Controller(AgentChatV2Service service) {
        this.service = service;
    }

    @PostMapping("/api/v2/agent/chat")
    public ResponseEntity<ApiResponse<AgentChatV2Response>> chat(@Valid @RequestBody AgentChatV2Request request) {
        Long userId = AuthContext.requireUserId();
        try {
            AgentChatV2Response response = service.chat(userId.longValue(), request);
            return ResponseEntity.ok(ApiResponse.success(response, response.getTraceId()));
        } catch (AgentChatConflictException ex) {
            HttpStatus status = conflictStatus(ex.getCode());
            return ResponseEntity.status(status).body(ApiResponse.<AgentChatV2Response>error(
                    errorCode(ex.getCode()), ex.getCode() + ": " + ex.getMessage(), null));
        } catch (AgentGatewayException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.<AgentChatV2Response>error(50201, ex.getCode() + ": " + ex.getMessage(), null));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<AgentChatV2Response>error(50001, "AGENT_CHAT_FAILED: " + ex.getMessage(), null));
        }
    }

    @PostMapping("/internal/agent/reports/{reportId}/sections")
    public ResponseEntity<Map<String, Object>> reportSections(
            @PathVariable Long reportId,
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String suppliedKey,
            @RequestBody Map<String, Object> request
    ) {
        if (!validInternalKey(suppliedKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(statusBody("FORBIDDEN", reportId, "invalid_internal_api_key"));
        }
        Map<String, Object> body = service.loadReportSections(reportId, request);
        String resultStatus = String.valueOf(body.get("status"));
        if ("NOT_FOUND".equals(resultStatus)) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        if ("VERSION_MISMATCH".equals(resultStatus)) return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        if (!"OK".equals(resultStatus)) return ResponseEntity.badRequest().body(body);
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> statusBody(String status, Long reportId, String error) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("status", status);
        body.put("report_id", reportId);
        body.put("error", error);
        return body;
    }

    private boolean validInternalKey(String suppliedKey) {
        if (internalApiKey == null || internalApiKey.trim().isEmpty() || suppliedKey == null) return false;
        return MessageDigest.isEqual(
                internalApiKey.trim().getBytes(StandardCharsets.UTF_8),
                suppliedKey.getBytes(StandardCharsets.UTF_8));
    }

    private HttpStatus conflictStatus(String code) {
        if ("IDEMPOTENCY_CONFLICT".equals(code) || "AGENT_REQUEST_IN_PROGRESS".equals(code)) return HttpStatus.CONFLICT;
        if ("REPORT_NOT_FOUND_OR_FORBIDDEN".equals(code)) return HttpStatus.NOT_FOUND;
        if ("AGENT_TURN_MISMATCH".equals(code) || "INVALID_AGENT_RESPONSE".equals(code)
                || "IDEMPOTENCY_RESPONSE_CORRUPTED".equals(code)) return HttpStatus.BAD_GATEWAY;
        return HttpStatus.BAD_REQUEST;
    }

    private int errorCode(String code) {
        if ("IDEMPOTENCY_CONFLICT".equals(code)) return 40901;
        if ("AGENT_REQUEST_IN_PROGRESS".equals(code)) return 40902;
        if ("REPORT_NOT_FOUND_OR_FORBIDDEN".equals(code)) return 40401;
        if ("AGENT_TURN_MISMATCH".equals(code)) return 50202;
        if ("INVALID_AGENT_RESPONSE".equals(code)) return 50203;
        return 40001;
    }
}

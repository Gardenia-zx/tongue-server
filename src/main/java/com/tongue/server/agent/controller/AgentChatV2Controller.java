package com.tongue.server.agent.controller;

import com.tongue.server.agent.dto.AgentChatV2Request;
import com.tongue.server.agent.dto.AgentChatV2Response;
import com.tongue.server.agent.service.AgentChatV2Service;
import com.tongue.server.agent.service.AgentChatTurnStore.AgentChatConflictException;
import com.tongue.server.agent.service.AgentGatewayClientV2.AgentGatewayException;
import com.tongue.server.auth.AuthContext;
import com.tongue.server.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v2/agent")
public class AgentChatV2Controller {

    private final AgentChatV2Service service;

    public AgentChatV2Controller(AgentChatV2Service service) {
        this.service = service;
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AgentChatV2Response>> chat(
            @Valid @RequestBody AgentChatV2Request request
    ) {
        Long userId = AuthContext.requireUserId();
        try {
            AgentChatV2Response response = service.chat(userId.longValue(), request);
            return ResponseEntity.ok(ApiResponse.success(response, response.getTraceId()));
        } catch (AgentChatConflictException ex) {
            HttpStatus status = conflictStatus(ex.getCode());
            return ResponseEntity.status(status)
                    .body(ApiResponse.<AgentChatV2Response>error(
                            errorCode(ex.getCode()),
                            ex.getCode() + ": " + ex.getMessage(),
                            null
                    ));
        } catch (AgentGatewayException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.<AgentChatV2Response>error(50201, ex.getCode() + ": " + ex.getMessage(), null));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<AgentChatV2Response>error(50001, "AGENT_CHAT_FAILED: " + ex.getMessage(), null));
        }
    }

    private HttpStatus conflictStatus(String code) {
        if ("IDEMPOTENCY_CONFLICT".equals(code) || "AGENT_REQUEST_IN_PROGRESS".equals(code)) {
            return HttpStatus.CONFLICT;
        }
        if ("REPORT_NOT_FOUND_OR_FORBIDDEN".equals(code)) {
            return HttpStatus.NOT_FOUND;
        }
        if ("AGENT_TURN_MISMATCH".equals(code)
                || "INVALID_AGENT_RESPONSE".equals(code)
                || "IDEMPOTENCY_RESPONSE_CORRUPTED".equals(code)) {
            return HttpStatus.BAD_GATEWAY;
        }
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

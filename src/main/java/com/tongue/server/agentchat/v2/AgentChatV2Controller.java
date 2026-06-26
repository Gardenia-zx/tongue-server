package com.tongue.server.agentchat.v2;

import com.tongue.server.agentchat.v2.AgentChatTurnStore.AgentChatConflictException;
import com.tongue.server.agentchat.v2.AgentGatewayClientV2.AgentGatewayException;
import com.tongue.server.common.ApiResponse;
import com.tongue.server.config.AuthProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/v2/agent")
public class AgentChatV2Controller {

    private final AgentChatV2Service service;
    private final AuthProperties authProperties;

    public AgentChatV2Controller(AgentChatV2Service service, AuthProperties authProperties) {
        this.service = service;
        this.authProperties = authProperties;
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AgentChatV2Response>> chat(
            @Valid @RequestBody AgentChatV2Request request,
            HttpServletRequest servletRequest,
            @RequestHeader(value = "X-User-Id", required = false) Long developmentUserId
    ) {
        Long userId = resolveUserId(servletRequest, developmentUserId);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.<AgentChatV2Response>error(40101, "未登录或登录状态已失效", null));
        }

        try {
            AgentChatV2Response response = service.chat(userId.longValue(), request);
            return ResponseEntity.ok(ApiResponse.success(response, response.getTraceId()));
        } catch (AgentChatConflictException ex) {
            int code = "IDEMPOTENCY_CONFLICT".equals(ex.getCode()) ? 40901 : 40902;
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.<AgentChatV2Response>error(code, ex.getCode() + ": " + ex.getMessage(), null));
        } catch (AgentGatewayException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.<AgentChatV2Response>error(50201, ex.getCode() + ": " + ex.getMessage(), null));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<AgentChatV2Response>error(50001, "AGENT_CHAT_FAILED: " + ex.getMessage(), null));
        }
    }

    private Long resolveUserId(HttpServletRequest request, Long developmentUserId) {
        String[] attributeNames = {"userId", "currentUserId", "authenticatedUserId"};
        for (String attributeName : attributeNames) {
            Object value = request.getAttribute(attributeName);
            Long parsed = parseUserId(value);
            if (parsed != null) {
                return parsed;
            }
        }
        if (authProperties.isAllowDevUserId()) {
            return developmentUserId;
        }
        return null;
    }

    private Long parseUserId(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.valueOf((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}

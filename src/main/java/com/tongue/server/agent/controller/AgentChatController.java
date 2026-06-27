package com.tongue.server.agent.controller;

import com.tongue.server.agent.dto.AgentChatRequest;
import com.tongue.server.agent.dto.AgentChatResponse;
import com.tongue.server.agent.dto.AgentConversationResponse;
import com.tongue.server.agent.context.service.ConversationContextService;
import com.tongue.server.agent.service.AgentChatService;
import com.tongue.server.auth.AuthContext;
import com.tongue.server.common.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/agent")
public class AgentChatController {

    private final AgentChatService agentChatService;
    private final ConversationContextService conversationContextService;

    public AgentChatController(
            AgentChatService agentChatService,
            ConversationContextService conversationContextService
    ) {
        this.agentChatService = agentChatService;
        this.conversationContextService = conversationContextService;
    }

    @PostMapping("/chat")
    public ApiResponse<AgentChatResponse> chat(@Valid @RequestBody AgentChatRequest request) {
        return ApiResponse.success(
                agentChatService.chat(request)
        );
    }

    @GetMapping("/conversations/current")
    public ApiResponse<AgentConversationResponse> currentConversation() {
        return ApiResponse.success(
                conversationContextService.currentConversation(AuthContext.requireUserId())
        );
    }

    @PostMapping("/conversations")
    public ApiResponse<AgentConversationResponse> createConversation() {
        return ApiResponse.success(
                conversationContextService.createConversation(AuthContext.requireUserId())
        );
    }

    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<AgentConversationResponse> getConversation(
            @PathVariable String conversationId
    ) {
        return ApiResponse.success(
                conversationContextService.getConversation(AuthContext.requireUserId(), conversationId)
        );
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ApiResponse<Object> deleteConversation(@PathVariable String conversationId) {
        conversationContextService.deleteConversation(AuthContext.requireUserId(), conversationId);
        return ApiResponse.success(null);
    }
}

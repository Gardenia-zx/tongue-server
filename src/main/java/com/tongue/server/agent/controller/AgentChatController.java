package com.tongue.server.agent.controller;

import com.tongue.server.agent.dto.AgentChatRequest;
import com.tongue.server.agent.dto.AgentChatResponse;
import com.tongue.server.agent.service.AgentChatService;
import com.tongue.server.common.ApiResponse;
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

    public AgentChatController(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    @PostMapping("/chat")
    public ApiResponse<AgentChatResponse> chat(@Valid @RequestBody AgentChatRequest request) {
        return ApiResponse.success(
                agentChatService.chat(request.message, request.threadId, request.conversationId)
        );
    }
}

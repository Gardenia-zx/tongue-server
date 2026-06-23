package com.tongue.server.client;

import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import com.tongue.server.config.AgentProperties;
import com.tongue.server.dto.AgentRunRequest;
import com.tongue.server.dto.AgentRunResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class TongueAgentClient {

    private final RestTemplate restTemplate;
    private final AgentProperties agentProperties;

    public TongueAgentClient(
            RestTemplate restTemplate,
            AgentProperties agentProperties
    ) {
        this.restTemplate = restTemplate;
        this.agentProperties = agentProperties;
    }

    public AgentRunResponse runAgent(AgentRunRequest request) {
        String url = buildRunUrl();
        try {
            AgentRunResponse response = restTemplate.postForObject(
                    url,
                    request,
                    AgentRunResponse.class
            );
            if (response == null) {
                throw new BusinessException(
                        ErrorCode.AGENT_CALL_FAILED,
                        "Python Agent 返回为空",
                        request.getTraceId()
                );
            }
            return response;
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                throw new BusinessException(
                        ErrorCode.AGENT_THREAD_BUSY,
                        "同一会话正在处理中，请稍后再试",
                        request.getTraceId(),
                        ex
                );
            }
            throw new BusinessException(
                    ErrorCode.AGENT_CALL_FAILED,
                    "调用 Python Agent 失败，HTTP 状态码：" + ex.getRawStatusCode(),
                    request.getTraceId(),
                    ex
            );
        } catch (ResourceAccessException ex) {
            throw new BusinessException(
                    ErrorCode.AGENT_CALL_FAILED,
                    "无法连接 Python Agent，请确认 tongue-agent 服务已启动",
                    request.getTraceId(),
                    ex
            );
        }
    }

    private String buildRunUrl() {
        String baseUrl = trimTrailingSlash(agentProperties.getBaseUrl());
        String path = agentProperties.getRunPath();
        if (path == null || path.trim().isEmpty()) {
            path = "/api/v1/agent/run";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return baseUrl + path;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}

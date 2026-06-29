package com.tongue.server.client;

import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import com.tongue.server.config.AgentProperties;
import com.tongue.server.dto.AgentRunRequest;
import com.tongue.server.dto.AgentRunResponse;
import com.tongue.server.dto.AgentTurnAckRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.Map;

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
            validateAnalysisResponse(request, response);
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
            if (isTimeout(ex)) {
                throw new BusinessException(
                        ErrorCode.AGENT_CALL_FAILED,
                        "调用 Python Agent 超时，可能是图像模型服务冷启动、RAG 或大模型生成耗时过长，请稍后查看任务状态或重试",
                        request.getTraceId(),
                        ex
                );
            }
            throw new BusinessException(
                    ErrorCode.AGENT_CALL_FAILED,
                    "无法连接 Python Agent，请确认 tongue-agent 服务已启动",
                    request.getTraceId(),
                    ex
            );
        }
    }

    public void ackTurn(AgentTurnAckRequest request) {
        try {
            restTemplate.postForObject(
                    buildAckUrl(),
                    request,
                    Object.class
            );
        } catch (Exception ex) {
            throw new BusinessException(
                    ErrorCode.AGENT_CALL_FAILED,
                    "调用 Python Agent Turn ACK 失败",
                    null,
                    ex
            );
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> explainReportCompare(Map<String, Object> request) {
        try {
            return restTemplate.postForObject(
                    buildCompareUrl(),
                    request,
                    Map.class
            );
        } catch (Exception ex) {
            throw new BusinessException(
                    ErrorCode.AGENT_CALL_FAILED,
                    "璋冪敤 Python Agent 鎶ュ憡瀵规瘮瑙ｉ噴澶辫触",
                    null,
                    ex
            );
        }
    }

    private void validateAnalysisResponse(AgentRunRequest request, AgentRunResponse response) {
        if (request == null
                || request.getClientContext() == null
                || !"tongue_analyze".equals(request.getClientContext().getPage())
                || !"COMPLETED".equals(response.getStatus())) {
            return;
        }

        Map<String, Object> payload = safeMap(safeMap(response.getNextAction()).get("payload"));
        Map<String, Object> draftReport = safeMap(payload.get("draft_report"));
        String answerType = text(payload.get("answer_type"));
        String reportStatus = text(draftReport.get("report_status"));

        if (draftReport.isEmpty()
                || !"TONGUE_REPORT".equals(answerType)
                || !("FINAL".equals(reportStatus) || "COMPLETED".equals(reportStatus))) {
            throw new BusinessException(
                    ErrorCode.AGENT_CALL_FAILED,
                    "Python Agent 未返回有效舌象报告",
                    request.getTraceId()
            );
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
        return value instanceof Map
                ? (Map<String, Object>) value
                : Collections.<String, Object>emptyMap();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String buildRunUrl() {
        return buildUrl(agentProperties.getRunPath(), "/api/v1/agent/run");
    }

    private String buildAckUrl() {
        return buildUrl(agentProperties.getAckPath(), "/api/v1/agent/turns/ack");
    }

    private String buildCompareUrl() {
        return buildUrl(agentProperties.getComparePath(), "/api/v1/agent/report-compare");
    }

    private String buildUrl(String configuredPath, String defaultPath) {
        String baseUrl = trimTrailingSlash(agentProperties.getBaseUrl());
        String path = configuredPath;
        if (path == null || path.trim().isEmpty()) {
            path = defaultPath;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return baseUrl + path;
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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

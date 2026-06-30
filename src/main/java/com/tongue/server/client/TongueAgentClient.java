package com.tongue.server.client;

import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import com.tongue.server.config.AgentProperties;
import com.tongue.server.dto.AgentRunRequest;
import com.tongue.server.dto.AgentRunResponse;
import com.tongue.server.dto.AgentTurnAckRequest;
import com.tongue.server.tongue.entity.TongueAnalysisTaskEntity;
import com.tongue.server.tongue.repository.TongueAnalysisTaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class TongueAgentClient {

    private final RestTemplate restTemplate;
    private final AgentProperties agentProperties;
    private final TongueAnalysisTaskRepository taskRepository;
    private final ScheduledExecutorService progressScheduler = Executors.newScheduledThreadPool(
            2,
            runnable -> {
                Thread thread = new Thread(runnable, "tongue-analysis-progress");
                thread.setDaemon(true);
                return thread;
            }
    );

    public TongueAgentClient(
            RestTemplate restTemplate,
            AgentProperties agentProperties,
            TongueAnalysisTaskRepository taskRepository
    ) {
        this.restTemplate = restTemplate;
        this.agentProperties = agentProperties;
        this.taskRepository = taskRepository;
    }

    public AgentRunResponse runAgent(AgentRunRequest request) {
        String url = buildRunUrl();
        List<ScheduledFuture<?>> progressJobs = scheduleAnalysisProgress(request);
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
        } finally {
            cancelProgressJobs(progressJobs);
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
                    "调用 Python Agent 报告对比解释失败",
                    null,
                    ex
            );
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> reviewHealthPlan(Map<String, Object> request) {
        try {
            return restTemplate.postForObject(
                    buildHealthPlanReviewUrl(),
                    request,
                    Map.class
            );
        } catch (Exception ex) {
            throw new BusinessException(
                    ErrorCode.AGENT_CALL_FAILED,
                    "调用 Python Agent 健康计划评估失败",
                    null,
                    ex
            );
        }
    }

    private List<ScheduledFuture<?>> scheduleAnalysisProgress(AgentRunRequest request) {
        if (!isDedicatedAnalysisRequest(request) || request.getTaskId() == null) {
            return Collections.emptyList();
        }

        Long taskId = request.getTaskId();
        List<ScheduledFuture<?>> jobs = new ArrayList<ScheduledFuture<?>>();
        jobs.add(progressScheduler.schedule(
                () -> advanceTaskStage(taskId, "RESULT_ANALYZING", 0.42),
                2,
                TimeUnit.SECONDS
        ));
        jobs.add(progressScheduler.schedule(
                () -> advanceTaskStage(taskId, "RAG_RETRIEVING", 0.62),
                5,
                TimeUnit.SECONDS
        ));
        jobs.add(progressScheduler.schedule(
                () -> advanceTaskStage(taskId, "REPORT_GENERATING", 0.78),
                9,
                TimeUnit.SECONDS
        ));
        return jobs;
    }

    private void advanceTaskStage(Long taskId, String nextStage, double nextProgress) {
        try {
            TongueAnalysisTaskEntity task = taskRepository.findById(taskId).orElse(null);
            if (task == null || !"RUNNING".equals(task.status)) {
                return;
            }
            if (stageRank(task.currentStage) >= stageRank(nextStage)) {
                return;
            }

            task.currentStage = nextStage;
            task.progress = Math.max(task.progress == null ? 0.0 : task.progress, nextProgress);
            taskRepository.save(task);
        } catch (Exception ignored) {
            // Progress heartbeat must never fail the actual analysis request.
        }
    }

    private int stageRank(String stage) {
        if ("MODEL_ANALYZING".equals(stage)) {
            return 1;
        }
        if ("RESULT_ANALYZING".equals(stage)) {
            return 2;
        }
        if ("RAG_RETRIEVING".equals(stage)) {
            return 3;
        }
        if ("REPORT_GENERATING".equals(stage)) {
            return 4;
        }
        if ("REPORT_READY".equals(stage) || "COMPLETED".equals(stage)) {
            return 5;
        }
        return 0;
    }

    private void cancelProgressJobs(List<ScheduledFuture<?>> jobs) {
        for (ScheduledFuture<?> job : jobs) {
            job.cancel(false);
        }
    }

    private boolean isDedicatedAnalysisRequest(AgentRunRequest request) {
        return request != null
                && request.getClientContext() != null
                && "tongue_analyze".equals(request.getClientContext().getPage());
    }

    private void validateAnalysisResponse(AgentRunRequest request, AgentRunResponse response) {
        if (!isDedicatedAnalysisRequest(request)
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

    private String buildHealthPlanReviewUrl() {
        return buildUrl(null, "/api/v1/agent/health-plan/review");
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

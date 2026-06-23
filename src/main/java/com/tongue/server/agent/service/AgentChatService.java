package com.tongue.server.agent.service;

import com.tongue.server.agent.dto.AgentChatResponse;
import com.tongue.server.auth.AuthContext;
import com.tongue.server.client.TongueAgentClient;
import com.tongue.server.dto.AgentRunRequest;
import com.tongue.server.dto.AgentRunResponse;
import com.tongue.server.tongue.entity.TongueReportEntity;
import com.tongue.server.tongue.repository.TongueReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentChatService {

    private final TongueAgentClient tongueAgentClient;
    private final TongueReportRepository reportRepository;

    public AgentChatService(TongueAgentClient tongueAgentClient, TongueReportRepository reportRepository) {
        this.tongueAgentClient = tongueAgentClient;
        this.reportRepository = reportRepository;
    }

    public AgentChatResponse chat(String messageText, String threadId, String conversationId) {
        Long userId = AuthContext.requireUserId();
        String normalizedThreadId = StringUtils.hasText(threadId)
                ? threadId.trim()
                : "web_chat_" + userId + "_" + UUID.randomUUID();

        AgentRunRequest.AgentMessage message = new AgentRunRequest.AgentMessage();
        message.setRole("user");
        message.setContentType("text");
        message.setContent(messageText.trim());
        message.setAttachments(new ArrayList<AgentRunRequest.AgentAttachment>());

        Map<String, Object> extra = new LinkedHashMap<String, Object>();
        extra.put("source", "tongue-server-chat");
        TongueReportEntity latestReport = findLatestUsableReport(userId);
        if (latestReport != null) {
            extra.put("latest_report", toLatestReportContext(latestReport));
        }

        AgentRunRequest.AgentClientContext clientContext = new AgentRunRequest.AgentClientContext();
        clientContext.setPage("ai_chat");
        if (latestReport != null) {
            clientContext.setActiveReportId(latestReport.id);
        }
        clientContext.setDeviceType("web");
        clientContext.setLocale("zh-CN");
        clientContext.setExtra(extra);

        Map<String, Object> memory = new LinkedHashMap<String, Object>();
        memory.put("can_read", true);
        memory.put("can_write", true);
        Map<String, Object> options = new LinkedHashMap<String, Object>();
        options.put("memory", memory);

        AgentRunRequest request = new AgentRunRequest();
        request.setSchemaVersion("1.0");
        request.setRequestId(UUID.randomUUID().toString());
        request.setTraceId("trace_chat_" + UUID.randomUUID());
        request.setUserId(userId);
        request.setThreadId(normalizedThreadId);
        request.setConversationId(conversationId);
        if (latestReport != null) {
            request.setReportId(latestReport.id);
        }
        request.setMessage(message);
        request.setClientContext(clientContext);
        request.setOptions(options);

        AgentRunResponse agentResponse = tongueAgentClient.runAgent(request);
        AgentChatResponse response = new AgentChatResponse();
        response.status = agentResponse.getStatus();
        response.threadId = agentResponse.getThreadId();
        response.conversationId = agentResponse.getConversationId();
        response.intentResult = agentResponse.getIntentResult();
        response.nextAction = agentResponse.getNextAction();
        response.content = extractContent(agentResponse);
        return response;
    }

    private TongueReportEntity findLatestUsableReport(Long userId) {
        List<TongueReportEntity> reports = reportRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
        for (TongueReportEntity report : reports) {
            if (StringUtils.hasText(report.summary) || StringUtils.hasText(report.featureSummary)) {
                return report;
            }
        }
        return null;
    }

    private Map<String, Object> toLatestReportContext(TongueReportEntity report) {
        Map<String, Object> context = new LinkedHashMap<String, Object>();
        context.put("report_id", report.id);
        context.put("task_id", report.taskId);
        context.put("report_status", report.reportStatus);
        context.put("summary", truncate(report.summary, 1200));
        context.put("feature_summary", truncate(report.featureSummary, 600));
        context.put("detected_feature_codes", truncate(report.detectedFeatureCodes, 1000));
        context.put("rag_query", truncate(report.ragQuery, 600));
        context.put("created_at", report.createdAt == null ? null : report.createdAt.toString());
        return context;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength) + "...";
    }

    private String extractContent(AgentRunResponse agentResponse) {
        Map<String, Object> message = agentResponse.getMessage();
        if (message == null) {
            return "";
        }
        Object content = message.get("content");
        return content == null ? "" : String.valueOf(content);
    }
}

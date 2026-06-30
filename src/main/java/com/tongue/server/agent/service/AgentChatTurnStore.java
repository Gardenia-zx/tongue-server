package com.tongue.server.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tongue.server.agent.context.entity.AgentChatConversationEntity;
import com.tongue.server.agent.context.entity.AgentChatMessageEntity;
import com.tongue.server.agent.context.entity.AgentChatTurnEntity;
import com.tongue.server.agent.context.repository.AgentChatConversationRepository;
import com.tongue.server.agent.context.repository.AgentChatMessageRepository;
import com.tongue.server.agent.context.repository.AgentChatTurnRepository;
import com.tongue.server.agent.dto.AgentChatV2Request;
import com.tongue.server.agent.dto.AgentChatV2Response;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AgentChatTurnStore {
    private static final int DEFAULT_THREAD_EPOCH = 1;

    private final AgentChatConversationRepository conversationRepository;
    private final AgentChatTurnRepository turnRepository;
    private final AgentChatMessageRepository messageRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AgentChatTurnStore(
            AgentChatConversationRepository conversationRepository,
            AgentChatTurnRepository turnRepository,
            AgentChatMessageRepository messageRepository,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.conversationRepository = conversationRepository;
        this.turnRepository = turnRepository;
        this.messageRepository = messageRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BeginTurnResult begin(
            long userId,
            AgentChatV2Request request,
            String conversationId,
            String turnId,
            String assistantMessageId,
            String traceId,
            String requestHash,
            String bindingMode,
            Long requestedReportId
    ) {
        Optional<AgentChatTurnEntity> existing = turnRepository.findByUserIdAndRequestId(userId, request.getRequestId());
        if (existing.isPresent()) {
            return replayOrReject(existing.get(), requestHash);
        }

        AgentChatConversationEntity conversation = resolveConversation(userId, conversationId, request.getThreadId());
        if (isBlank(conversation.getThreadId())) {
            conversation.setThreadId(request.getThreadId());
        }

        Long resolvedReportId = resolveBoundReportId(
                userId,
                conversation,
                request.getThreadId(),
                bindingMode,
                requestedReportId,
                request.getMessage() == null ? "" : request.getMessage().getContent()
        );
        if (resolvedReportId != null) {
            conversation.setActiveReportId(resolvedReportId);
        }
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        String canonicalConversationId = conversation.getConversationId();

        LocalDateTime now = LocalDateTime.now();
        AgentChatTurnEntity turn = new AgentChatTurnEntity();
        turn.setTurnId(turnId);
        turn.setConversationId(canonicalConversationId);
        turn.setUserId(userId);
        turn.setRequestId(request.getRequestId());
        turn.setRequestHash(requestHash);
        turn.setStatus("PROCESSING");
        turn.setInputContent(request.getMessage().getContent());
        turn.setContextBinding(bindingMode);
        turn.setBoundReportId(resolvedReportId);
        turn.setTraceId(traceId);
        turn.setStartedAt(now);
        turn.setCreatedAt(now);
        turn.setUpdatedAt(now);

        try {
            turnRepository.saveAndFlush(turn);
        } catch (DataIntegrityViolationException race) {
            AgentChatTurnEntity raced = turnRepository.findByUserIdAndRequestId(userId, request.getRequestId())
                    .orElseThrow(() -> race);
            return replayOrReject(raced, requestHash);
        }

        if (!messageRepository.existsByMessageId(request.getClientMessageId())) {
            AgentChatMessageEntity userMessage = new AgentChatMessageEntity();
            userMessage.setMessageId(request.getClientMessageId());
            userMessage.setTurnId(turnId);
            userMessage.setConversationId(canonicalConversationId);
            userMessage.setUserId(userId);
            userMessage.setRole("USER");
            userMessage.setContentType("text");
            userMessage.setContent(request.getMessage().getContent());
            userMessage.setReportId(null);
            userMessage.setStatus("COMPLETED");
            userMessage.setSequenceNo(messageRepository.countByUserIdAndConversationId(userId, canonicalConversationId) + 1L);
            userMessage.setCreatedAt(now);
            userMessage.setUpdatedAt(now);
            messageRepository.save(userMessage);
        }

        BeginTurnResult result = new BeginTurnResult();
        result.setNewTurn(true);
        result.setTurn(turn);
        result.setConversation(conversation);
        result.setAssistantMessageId(assistantMessageId);
        return result;
    }

    private Long resolveBoundReportId(
            long userId,
            AgentChatConversationEntity conversation,
            String threadId,
            String bindingMode,
            Long requestedReportId,
            String messageText
    ) {
        if ("NONE".equals(bindingMode)) {
            return null;
        }

        if ("ACTIVE_REPORT".equals(bindingMode)) {
            if (requestedReportId == null) {
                throw new AgentChatConflictException("REPORT_ID_REQUIRED", "ACTIVE_REPORT 模式必须提供 report_id");
            }
            loadTrustedReportRef(userId, requestedReportId);
            return requestedReportId;
        }

        if ("LAST_ANSWER".equals(bindingMode)) {
            Optional<AgentChatMessageEntity> recent = messageRepository
                    .findFirstByUserIdAndConversationIdAndRoleAndReportIdIsNotNullOrderBySequenceNoDesc(
                            userId,
                            conversation.getConversationId(),
                            "ASSISTANT"
                    );
            if (recent.isPresent()) {
                return recent.get().getReportId();
            }
        }

        if (conversation.getActiveReportId() != null) {
            return validateOptionalReport(userId, conversation.getActiveReportId());
        }

        Long legacyActiveReportId = findLegacyActiveReportId(userId, threadId);
        if (legacyActiveReportId != null) {
            return validateOptionalReport(userId, legacyActiveReportId);
        }

        Long latestThreadReportId = findLatestThreadReportId(userId, threadId);
        if (latestThreadReportId != null) {
            return validateOptionalReport(userId, latestThreadReportId);
        }
        if ("AUTO".equals(bindingMode) && mentionsReport(messageText)) {
            return validateOptionalReport(userId, findLatestUserReportId(userId));
        }
        return null;
    }

    private boolean mentionsReport(String text) {
        if (text == null) {
            return false;
        }
        String compact = text.replaceAll("\\s+", "");
        return compact.contains("报告")
                || compact.contains("舌象结果")
                || compact.contains("分析结果")
                || compact.contains("识别结果");
    }

    private Long validateOptionalReport(long userId, Long reportId) {
        if (reportId == null) {
            return null;
        }
        try {
            loadTrustedReportRef(userId, reportId);
            return reportId;
        } catch (AgentChatConflictException ignored) {
            return null;
        }
    }

    private Long findLegacyActiveReportId(long userId, String threadId) {
        if (threadId == null || threadId.trim().isEmpty()) {
            return null;
        }
        List<Long> rows = jdbcTemplate.queryForList(
                "SELECT active_report_id FROM agent_conversation " +
                        "WHERE user_id = ? AND thread_id = ? AND status = 'ACTIVE' " +
                        "AND active_report_id IS NOT NULL ORDER BY updated_at DESC LIMIT 1",
                Long.class,
                userId,
                threadId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Long findLatestThreadReportId(long userId, String threadId) {
        if (threadId == null || threadId.trim().isEmpty()) {
            return null;
        }
        List<Long> rows = jdbcTemplate.queryForList(
                "SELECT id FROM tongue_report " +
                        "WHERE user_id = ? AND thread_id = ? AND deleted_at IS NULL " +
                        "ORDER BY updated_at DESC, id DESC LIMIT 1",
                Long.class,
                userId,
                threadId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Long findLatestUserReportId(long userId) {
        List<Long> rows = jdbcTemplate.queryForList(
                "SELECT id FROM tongue_report " +
                        "WHERE user_id = ? AND deleted_at IS NULL " +
                        "ORDER BY updated_at DESC, id DESC LIMIT 1",
                Long.class,
                userId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private BeginTurnResult replayOrReject(AgentChatTurnEntity turn, String requestHash) {
        if (!requestHash.equals(turn.getRequestHash())) {
            throw new AgentChatConflictException("IDEMPOTENCY_CONFLICT", "相同 request_id 对应了不同请求内容");
        }
        if ("COMPLETED".equals(turn.getStatus()) && turn.getResponseJson() != null) {
            BeginTurnResult result = new BeginTurnResult();
            result.setNewTurn(false);
            result.setTurn(turn);
            try {
                result.setReplayResponse(objectMapper.readValue(turn.getResponseJson(), AgentChatV2Response.class));
            } catch (JsonProcessingException ex) {
                throw new AgentChatConflictException("IDEMPOTENCY_RESPONSE_CORRUPTED", "已完成请求的持久化响应无法解析");
            }
            return result;
        }
        throw new AgentChatConflictException("AGENT_REQUEST_IN_PROGRESS", "该 request_id 正在处理中，请稍后使用同一 request_id 重试");
    }

    private AgentChatConversationEntity newConversation(long userId, String conversationId, String threadId) {
        LocalDateTime now = LocalDateTime.now();
        AgentChatConversationEntity conversation = new AgentChatConversationEntity();
        conversation.setConversationId(conversationId);
        conversation.setUserId(userId);
        conversation.setThreadId(threadId);
        conversation.setThreadEpoch(DEFAULT_THREAD_EPOCH);
        conversation.setStatus("ACTIVE");
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        return conversation;
    }

    private AgentChatConversationEntity resolveConversation(long userId, String conversationId, String threadId) {
        Optional<AgentChatConversationEntity> byConversation = conversationRepository
                .findByUserIdAndConversationId(userId, conversationId);
        if (byConversation.isPresent()) {
            return byConversation.get();
        }
        if (!isBlank(threadId)) {
            Optional<AgentChatConversationEntity> byThread = conversationRepository
                    .findByUserIdAndThreadIdAndThreadEpoch(userId, threadId, DEFAULT_THREAD_EPOCH);
            if (byThread.isPresent()) {
                return byThread.get();
            }
        }
        return newConversation(userId, conversationId, threadId);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Transactional(readOnly = true)
    public List<AgentChatMessageEntity> recentMessages(long userId, String conversationId) {
        List<AgentChatMessageEntity> rows = new ArrayList<AgentChatMessageEntity>(
                messageRepository.findByUserIdAndConversationIdOrderBySequenceNoDesc(
                        userId,
                        conversationId,
                        PageRequest.of(0, 12)
                )
        );
        Collections.reverse(rows);
        return rows;
    }

    @Transactional(readOnly = true)
    public JsonNode loadOwnedReport(long userId, Long reportId) {
        return loadTrustedReportRef(userId, reportId);
    }

    @Transactional(readOnly = true)
    public JsonNode loadTrustedReportRef(long userId, Long reportId) {
        if (reportId == null) {
            return null;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, user_id, task_id, thread_id, report_status, summary, feature_summary, created_at, updated_at " +
                        "FROM tongue_report WHERE id = ? AND user_id = ? AND deleted_at IS NULL",
                reportId,
                userId
        );
        if (rows.isEmpty()) {
            throw new AgentChatConflictException("REPORT_NOT_FOUND_OR_FORBIDDEN", "报告不存在或不属于当前用户");
        }

        Map<String, Object> row = rows.get(0);
        ObjectNode ref = objectMapper.createObjectNode();
        ref.put("report_id", ((Number) row.get("id")).longValue());
        ref.put("owner_user_id", userId);
        ref.put("tenant_id", String.valueOf(userId));
        ref.put("trusted", true);
        ref.put("is_current_active_report", true);
        ref.put("report_version", loadCurrentReportVersion(reportId));
        putNullable(ref, "task_id", row.get("task_id"));
        putNullable(ref, "thread_id", row.get("thread_id"));
        putNullable(ref, "report_status", row.get("report_status"));
        putNullable(ref, "summary", row.get("summary"));
        putNullable(ref, "feature_summary", row.get("feature_summary"));
        putNullable(ref, "created_at", row.get("created_at"));
        putNullable(ref, "updated_at", row.get("updated_at"));

        messageRepository.findFirstByUserIdAndReportIdOrderBySequenceNoDesc(userId, reportId)
                .map(AgentChatMessageEntity::getTurnId)
                .ifPresent(value -> ref.put("source_turn_id", value));
        return ref;
    }

    private int loadCurrentReportVersion(Long reportId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_no), 1) FROM tongue_report_version WHERE report_id = ?",
                Integer.class,
                reportId
        );
        return value == null ? 1 : value.intValue();
    }

    private void putNullable(ObjectNode target, String key, Object value) {
        if (value instanceof Number) {
            target.put(key, ((Number) value).longValue());
        } else if (value != null) {
            target.put(key, String.valueOf(value));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void bindActiveReport(long userId, String conversationId, String threadId, long reportId) {
        loadTrustedReportRef(userId, reportId);
        AgentChatConversationEntity conversation = resolveConversation(userId, conversationId, threadId);
        if (isBlank(conversation.getThreadId())) {
            conversation.setThreadId(threadId);
        }
        conversation.setActiveReportId(reportId);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(
            AgentChatTurnEntity turn,
            String assistantMessageId,
            AgentChatV2Response response,
            Long reportId
    ) {
        AgentChatTurnEntity managed = turnRepository.findById(turn.getId())
                .orElseThrow(() -> new IllegalStateException("Agent turn disappeared before completion"));
        if (!managed.getTurnId().equals(response.getTurnId()) || !managed.getRequestId().equals(response.getRequestId())) {
            throw new AgentChatConflictException("AGENT_TURN_MISMATCH", "Agent 响应不属于当前 Turn");
        }

        LocalDateTime now = LocalDateTime.now();
        if (!messageRepository.existsByMessageId(assistantMessageId)) {
            AgentChatMessageEntity assistant = new AgentChatMessageEntity();
            assistant.setMessageId(assistantMessageId);
            assistant.setTurnId(managed.getTurnId());
            assistant.setConversationId(managed.getConversationId());
            assistant.setUserId(managed.getUserId());
            assistant.setRole("ASSISTANT");
            assistant.setContentType(response.getAssistantMessage().getContentType());
            assistant.setContent(response.getAssistantMessage().getContent());
            assistant.setStructuredContentJson(toJson(response.getAssistantMessage().getStructuredContent()));
            assistant.setReportId(reportId);
            assistant.setStatus("COMPLETED");
            assistant.setSequenceNo(messageRepository.countByUserIdAndConversationId(
                    managed.getUserId(), managed.getConversationId()) + 1L);
            assistant.setCreatedAt(now);
            assistant.setUpdatedAt(now);
            messageRepository.save(assistant);
        }

        managed.setStatus("COMPLETED");
        managed.setResponseJson(toJson(response));
        managed.setFinishedAt(now);
        managed.setUpdatedAt(now);
        turnRepository.save(managed);

        if (reportId != null) {
            AgentChatConversationEntity conversation = conversationRepository
                    .findByUserIdAndConversationId(managed.getUserId(), managed.getConversationId())
                    .orElse(null);
            if (conversation != null) {
                conversation.setActiveReportId(reportId);
                conversation.setUpdatedAt(now);
                conversationRepository.save(conversation);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(AgentChatTurnEntity turn, String errorCode, String errorMessage) {
        AgentChatTurnEntity managed = turnRepository.findById(turn.getId()).orElse(null);
        if (managed == null || "COMPLETED".equals(managed.getStatus())) {
            return;
        }
        managed.setStatus("FAILED");
        managed.setErrorCode(errorCode);
        managed.setErrorMessage(errorMessage);
        managed.setFinishedAt(LocalDateTime.now());
        managed.setUpdatedAt(LocalDateTime.now());
        turnRepository.save(managed);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize agent chat record", ex);
        }
    }

    public static class BeginTurnResult {
        private boolean newTurn;
        private AgentChatTurnEntity turn;
        private AgentChatConversationEntity conversation;
        private String assistantMessageId;
        private AgentChatV2Response replayResponse;

        public boolean isNewTurn() { return newTurn; }
        public void setNewTurn(boolean newTurn) { this.newTurn = newTurn; }
        public AgentChatTurnEntity getTurn() { return turn; }
        public void setTurn(AgentChatTurnEntity turn) { this.turn = turn; }
        public AgentChatConversationEntity getConversation() { return conversation; }
        public void setConversation(AgentChatConversationEntity conversation) { this.conversation = conversation; }
        public String getAssistantMessageId() { return assistantMessageId; }
        public void setAssistantMessageId(String assistantMessageId) { this.assistantMessageId = assistantMessageId; }
        public AgentChatV2Response getReplayResponse() { return replayResponse; }
        public void setReplayResponse(AgentChatV2Response replayResponse) { this.replayResponse = replayResponse; }
    }

    public static class AgentChatConflictException extends RuntimeException {
        private final String code;

        public AgentChatConflictException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() { return code; }
    }
}

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
            Long boundReportId
    ) {
        Optional<AgentChatTurnEntity> existing = turnRepository.findByUserIdAndRequestId(userId, request.getRequestId());
        if (existing.isPresent()) {
            return replayOrReject(existing.get(), requestHash);
        }

        AgentChatConversationEntity conversation = conversationRepository
                .findByUserIdAndConversationId(userId, conversationId)
                .orElseGet(() -> newConversation(userId, conversationId, request.getThreadId()));
        conversation.setThreadId(request.getThreadId());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        LocalDateTime now = LocalDateTime.now();
        AgentChatTurnEntity turn = new AgentChatTurnEntity();
        turn.setTurnId(turnId);
        turn.setConversationId(conversationId);
        turn.setUserId(userId);
        turn.setRequestId(request.getRequestId());
        turn.setRequestHash(requestHash);
        turn.setStatus("PROCESSING");
        turn.setInputContent(request.getMessage().getContent());
        turn.setContextBinding(bindingMode);
        turn.setBoundReportId(boundReportId);
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
            userMessage.setConversationId(conversationId);
            userMessage.setUserId(userId);
            userMessage.setRole("USER");
            userMessage.setContentType("text");
            userMessage.setContent(request.getMessage().getContent());
            userMessage.setReportId(null);
            userMessage.setStatus("COMPLETED");
            userMessage.setSequenceNo(messageRepository.countByUserIdAndConversationId(userId, conversationId) + 1L);
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
        conversation.setThreadEpoch(1);
        conversation.setStatus("ACTIVE");
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        return conversation;
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
        if (reportId == null) {
            return null;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, summary, feature_summary, report_status " +
                        "FROM tongue_report WHERE id = ? AND user_id = ? AND deleted_at IS NULL",
                reportId,
                userId
        );
        if (rows.isEmpty()) {
            throw new AgentChatConflictException("REPORT_NOT_FOUND_OR_FORBIDDEN", "报告不存在或不属于当前用户");
        }
        Map<String, Object> row = rows.get(0);
        ObjectNode report = objectMapper.createObjectNode();
        report.put("report_id", ((Number) row.get("id")).longValue());
        putNullable(report, "summary", row.get("summary"));
        putNullable(report, "feature_summary", row.get("feature_summary"));
        putNullable(report, "report_status", row.get("report_status"));
        return report;
    }

    private void putNullable(ObjectNode target, String key, Object value) {
        if (value != null) {
            target.put(key, String.valueOf(value));
        }
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

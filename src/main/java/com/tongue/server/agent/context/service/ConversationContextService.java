package com.tongue.server.agent.context.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongue.server.agent.context.entity.AgentContextSummaryEntity;
import com.tongue.server.agent.context.entity.AgentConversationEntity;
import com.tongue.server.agent.context.entity.AgentMemoryOutboxEntity;
import com.tongue.server.agent.context.entity.AgentMessageEntity;
import com.tongue.server.agent.context.repository.AgentContextSummaryRepository;
import com.tongue.server.agent.context.repository.AgentConversationRepository;
import com.tongue.server.agent.context.repository.AgentMemoryOutboxRepository;
import com.tongue.server.agent.context.repository.AgentMessageRepository;
import com.tongue.server.agent.dto.AgentConversationResponse;
import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import com.tongue.server.tongue.entity.TongueReportEntity;
import com.tongue.server.tongue.entity.TongueReportEvidenceEntity;
import com.tongue.server.tongue.repository.TongueReportEvidenceRepository;
import com.tongue.server.tongue.repository.TongueReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConversationContextService {

    private static final String ACTIVE = "ACTIVE";
    private static final int RECENT_MESSAGE_LIMIT = 6;
    private static final int COMPRESS_MESSAGE_THRESHOLD = 12;
    private static final int MAX_CONTEXT_CHARS = 8000;

    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentContextSummaryRepository summaryRepository;
    private final AgentMemoryOutboxRepository memoryOutboxRepository;
    private final TongueReportRepository reportRepository;
    private final TongueReportEvidenceRepository evidenceRepository;
    private final ObjectMapper objectMapper;

    public ConversationContextService(
            AgentConversationRepository conversationRepository,
            AgentMessageRepository messageRepository,
            AgentContextSummaryRepository summaryRepository,
            AgentMemoryOutboxRepository memoryOutboxRepository,
            TongueReportRepository reportRepository,
            TongueReportEvidenceRepository evidenceRepository,
            ObjectMapper objectMapper
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.summaryRepository = summaryRepository;
        this.memoryOutboxRepository = memoryOutboxRepository;
        this.reportRepository = reportRepository;
        this.evidenceRepository = evidenceRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AgentConversationEntity ensureConversation(
            Long userId,
            String requestedConversationId,
            String preferredThreadId
    ) {
        Long conversationId = parseConversationId(requestedConversationId);
        if (conversationId != null) {
            Optional<AgentConversationEntity> existing =
                    conversationRepository.findForUpdate(conversationId, userId, ACTIVE);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        AgentConversationEntity conversation = new AgentConversationEntity();
        conversation.userId = userId;
        conversation.title = "舌象健康会话";
        conversation.threadId = StringUtils.hasText(preferredThreadId)
                ? preferredThreadId.trim()
                : "conversation_" + userId + "_" + UUID.randomUUID();
        conversation.status = ACTIVE;
        return conversationRepository.save(conversation);
    }

    @Transactional
    public AgentConversationResponse currentConversation(Long userId) {
        AgentConversationEntity conversation = conversationRepository
                .findFirstByUserIdAndStatusOrderByUpdatedAtDesc(userId, ACTIVE)
                .orElseGet(() -> ensureConversation(userId, null, null));
        return toConversationResponse(conversation, 80);
    }

    @Transactional(readOnly = true)
    public AgentConversationResponse getConversation(Long userId, String conversationId) {
        AgentConversationEntity conversation = loadConversation(userId, conversationId);
        return toConversationResponse(conversation, 80);
    }

    @Transactional
    public AgentConversationResponse createConversation(Long userId) {
        return toConversationResponse(ensureConversation(userId, null, null), 80);
    }

    @Transactional
    public void deleteConversation(Long userId, String conversationId) {
        AgentConversationEntity conversation = loadConversation(userId, conversationId);
        conversation.status = "DELETED";
        conversationRepository.save(conversation);
    }

    @Transactional
    public AgentMessageEntity appendUserMessage(
            AgentConversationEntity conversation,
            String content,
            String contentType,
            Long imageFileId,
            Long reportId,
            Map<String, Object> metadata
    ) {
        return appendUserMessage(
                conversation,
                content,
                contentType,
                imageFileId,
                reportId,
                metadata,
                null
        );
    }

    @Transactional
    public AgentMessageEntity appendUserMessage(
            AgentConversationEntity conversation,
            String content,
            String contentType,
            Long imageFileId,
            Long reportId,
            Map<String, Object> metadata,
            String externalMessageId
    ) {
        return appendMessage(
                conversation,
                "user",
                content,
                contentType,
                imageFileId,
                reportId,
                metadata,
                externalMessageId
        );
    }

    @Transactional
    public AgentMessageEntity appendAssistantMessage(
            AgentConversationEntity conversation,
            String content,
            Long reportId,
            Map<String, Object> metadata
    ) {
        return appendAssistantMessage(conversation, content, reportId, metadata, null);
    }

    @Transactional
    public AgentMessageEntity appendAssistantMessage(
            AgentConversationEntity conversation,
            String content,
            Long reportId,
            Map<String, Object> metadata,
            String externalMessageId
    ) {
        return appendMessage(
                conversation,
                "assistant",
                content,
                "text",
                null,
                reportId,
                metadata,
                externalMessageId
        );
    }

    @Transactional
    public AgentMessageEntity appendAssistantMessageAndMemoryOutbox(
            AgentConversationEntity conversation,
            String content,
            Long reportId,
            Map<String, Object> metadata,
            String externalMessageId,
            String tenantId,
            String turnId,
            String userMessageId,
            Integer threadEpoch
    ) {
        AgentMessageEntity message = appendMessage(
                conversation,
                "assistant",
                content,
                "text",
                null,
                reportId,
                metadata,
                externalMessageId
        );
        writeMemoryOutboxIfAbsent(
                conversation,
                tenantId,
                turnId,
                userMessageId,
                externalMessageId,
                reportId,
                threadEpoch,
                metadata
        );
        return message;
    }

    @Transactional
    public void markImageSubmitted(AgentConversationEntity conversation, Long imageFileId) {
        conversation.lastImageFileId = imageFileId;
        conversationRepository.save(conversation);
    }

    @Transactional
    public void markReportReady(String conversationId, Long userId, TongueReportEntity report, String assistantContent) {
        markReportReady(conversationId, userId, report, assistantContent, null);
    }

    @Transactional
    public void markReportReady(
            String conversationId,
            Long userId,
            TongueReportEntity report,
            String assistantContent,
            String externalMessageId
    ) {
        markReportReady(
                conversationId,
                userId,
                report,
                assistantContent,
                externalMessageId,
                null,
                null,
                null,
                null
        );
    }

    @Transactional
    public void markReportReady(
            String conversationId,
            Long userId,
            TongueReportEntity report,
            String assistantContent,
            String externalMessageId,
            String tenantId,
            String turnId,
            String userMessageId,
            Integer threadEpoch
    ) {
        AgentConversationEntity conversation = resolveConversationForReport(userId, conversationId, report);
        conversation.activeReportId = report.id;
        conversation.lastImageFileId = report.imageFileId;
        conversationRepository.save(conversation);

        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("source", "agent_report");
        metadata.put("task_id", report.taskId);
        metadata.put("node_name", "tongue_report_node");
        metadata.put("route_target", "tongue_analysis_subgraph");
        metadata.put("answer_type", "TONGUE_REPORT");
        metadata.put("rag_query", report.ragQuery);
        metadata.put("report_id", report.id);
        Map<String, Object> structuredContent = extractStructuredContentFromDraftReport(report.draftReportJson);
        if (!structuredContent.isEmpty()) {
            metadata.put("structured_content", structuredContent);
        }
        if (StringUtils.hasText(turnId)) {
            appendAssistantMessageAndMemoryOutbox(
                    conversation,
                    assistantContent,
                    report.id,
                    metadata,
                    externalMessageId,
                    tenantId,
                    turnId,
                    userMessageId,
                    threadEpoch
            );
        } else {
            appendAssistantMessage(conversation, assistantContent, report.id, metadata, externalMessageId);
        }
    }

    @Transactional
    public void clearActiveReport(Long userId, Long reportId) {
        for (AgentConversationEntity conversation : conversationRepository
                .findByUserIdAndActiveReportIdAndStatus(userId, reportId, ACTIVE)) {
            conversation.activeReportId = null;
            conversationRepository.save(conversation);
        }
    }

    @Transactional(readOnly = true)
    public Optional<AgentMessageEntity> findMessageByExternalId(
            AgentConversationEntity conversation,
            String externalMessageId
    ) {
        if (!StringUtils.hasText(externalMessageId)) {
            return Optional.empty();
        }
        return messageRepository.findFirstByConversationIdAndUserIdAndExternalMessageId(
                conversation.id,
                conversation.userId,
                externalMessageId
        );
    }

    @Transactional(readOnly = true)
    public String resolveConversationIdForReport(Long userId, Long reportId) {
        Optional<AgentMessageEntity> message =
                messageRepository.findFirstByUserIdAndReportIdOrderByCreatedAtDesc(userId, reportId);
        return message.map(value -> String.valueOf(value.conversationId)).orElse(null);
    }

    @Transactional
    public Map<String, Object> buildContextBundleForChat(
            AgentConversationEntity conversation,
            String userInput
    ) {
        AgentContextSummaryEntity summary = compressIfNeeded(conversation);
        return buildContextBundle(conversation, summary, conversation.activeReportId, userInput);
    }

    @Transactional
    public Map<String, Object> buildContextBundleForAnalysis(
            AgentConversationEntity conversation,
            Long currentReportId,
            String userInput
    ) {
        AgentContextSummaryEntity summary = compressIfNeeded(conversation);
        return buildContextBundle(conversation, summary, currentReportId, userInput);
    }

    private AgentMessageEntity appendMessage(
            AgentConversationEntity conversation,
            String role,
            String content,
            String contentType,
            Long imageFileId,
            Long reportId,
            Map<String, Object> metadata,
            String externalMessageId
    ) {
        if (StringUtils.hasText(externalMessageId)) {
            Optional<AgentMessageEntity> existing =
                    messageRepository.findFirstByConversationIdAndUserIdAndExternalMessageId(
                            conversation.id,
                            conversation.userId,
                            externalMessageId
                    );
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        AgentMessageEntity message = new AgentMessageEntity();
        message.conversationId = conversation.id;
        message.userId = conversation.userId;
        message.externalMessageId = externalMessageId;
        message.role = role;
        message.content = truncate(content, 5000);
        message.contentType = StringUtils.hasText(contentType) ? contentType : "text";
        message.imageFileId = imageFileId;
        message.reportId = reportId;
        message.metadataJson = writeJsonOrNull(metadata);
        AgentMessageEntity saved = messageRepository.save(message);
        if ("user".equals(role) && !StringUtils.hasText(conversation.title)) {
            conversation.title = truncate(content, 64);
        }
        conversationRepository.save(conversation);
        return saved;
    }

    private void writeMemoryOutboxIfAbsent(
            AgentConversationEntity conversation,
            String tenantId,
            String turnId,
            String userMessageId,
            String assistantMessageId,
            Long activeReportId,
            Integer threadEpoch,
            Map<String, Object> metadata
    ) {
        if (!StringUtils.hasText(turnId) || !StringUtils.hasText(assistantMessageId)) {
            return;
        }
        String eventId = "memory_turn_" + turnId;
        if (memoryOutboxRepository.findByEventId(eventId).isPresent()) {
            return;
        }

        Map<String, Object> payloadRef = new LinkedHashMap<String, Object>();
        payloadRef.put("tenant_id", tenantId);
        payloadRef.put("conversation_id", conversation.id);
        payloadRef.put("thread_id", conversation.threadId);
        payloadRef.put("thread_epoch", threadEpoch == null ? 1 : threadEpoch);
        payloadRef.put("turn_id", turnId);
        payloadRef.put("user_message_id", userMessageId);
        payloadRef.put("assistant_message_id", assistantMessageId);
        payloadRef.put("active_report_id", activeReportId);
        payloadRef.put("metadata", metadata);

        AgentMemoryOutboxEntity outbox = new AgentMemoryOutboxEntity();
        outbox.eventId = eventId;
        outbox.tenantId = StringUtils.hasText(tenantId) ? tenantId : String.valueOf(conversation.userId);
        outbox.userId = conversation.userId;
        outbox.conversationId = conversation.id;
        outbox.threadId = conversation.threadId;
        outbox.threadEpoch = threadEpoch == null ? 1 : threadEpoch;
        outbox.turnId = turnId;
        outbox.userMessageId = userMessageId;
        outbox.assistantMessageId = assistantMessageId;
        outbox.activeReportId = activeReportId;
        outbox.status = "NEW";
        outbox.payloadRefJson = writeJsonOrNull(payloadRef);
        memoryOutboxRepository.save(outbox);
    }

    private AgentConversationEntity resolveConversationForReport(
            Long userId,
            String conversationId,
            TongueReportEntity report
    ) {
        Long parsedId = parseConversationId(conversationId);
        if (parsedId != null) {
            Optional<AgentConversationEntity> existing =
                    conversationRepository.findForUpdate(parsedId, userId, ACTIVE);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        Optional<AgentMessageEntity> message =
                messageRepository.findFirstByUserIdAndReportIdOrderByCreatedAtDesc(userId, report.id);
        if (message.isPresent()) {
            Optional<AgentConversationEntity> existing =
                    conversationRepository.findForUpdate(message.get().conversationId, userId, ACTIVE);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        return ensureConversation(userId, null, report.threadId);
    }

    private AgentConversationEntity loadConversation(Long userId, String conversationId) {
        Long parsedId = parseConversationId(conversationId);
        if (parsedId == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "会话不存在");
        }
        return conversationRepository.findByIdAndUserIdAndStatus(parsedId, userId, ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "会话不存在"));
    }

    private AgentConversationResponse toConversationResponse(
            AgentConversationEntity conversation,
            int limit
    ) {
        AgentConversationResponse response = new AgentConversationResponse();
        response.conversationId = String.valueOf(conversation.id);
        response.threadId = conversation.threadId;
        response.activeReportId = conversation.activeReportId;
        response.lastImageFileId = conversation.lastImageFileId;
        response.latestReport = activeReportContext(conversation.activeReportId, conversation.userId);

        List<AgentMessageEntity> allMessages =
                messageRepository.findByConversationIdAndUserIdOrderByCreatedAtAsc(conversation.id, conversation.userId);
        int start = Math.max(0, allMessages.size() - limit);
        for (int index = start; index < allMessages.size(); index++) {
            response.messages.add(toMessageResponse(allMessages.get(index)));
        }
        return response;
    }

    private AgentConversationResponse.Message toMessageResponse(AgentMessageEntity entity) {
        AgentConversationResponse.Message message = new AgentConversationResponse.Message();
        message.messageId = entity.id;
        message.role = entity.role;
        message.content = entity.content;
        message.contentType = entity.contentType;
        message.imageFileId = entity.imageFileId;
        message.reportId = entity.reportId;
        message.createdAt = entity.createdAt == null ? null : entity.createdAt.toString();
        Map<String, Object> metadata = safeMap(parseJson(entity.metadataJson));
        Map<String, Object> structuredContent = safeMap(metadata.get("structured_content"));
        if (!structuredContent.isEmpty()) {
            message.structuredContent = structuredContent;
        }
        return message;
    }

    private Map<String, Object> buildContextBundle(
            AgentConversationEntity conversation,
            AgentContextSummaryEntity summary,
            Long activeReportId,
            String userInput
    ) {
        Map<String, Object> bundle = new LinkedHashMap<String, Object>();
        bundle.put("schema_version", "1.0");
        bundle.put("mode", "stateless_context_bundle");
        bundle.put("conversation_id", String.valueOf(conversation.id));
        bundle.put("thread_id", conversation.threadId);
        bundle.put("active_report", activeReportContext(activeReportId, conversation.userId));
        bundle.put("conversation_summary", toSummaryContext(summary));
        bundle.put("recent_messages", recentMessagesContext(conversation.id, conversation.userId));
        bundle.put("last_final_answer", lastFinalAnswerContext(conversation.id, conversation.userId));
        bundle.put("traceback_context", tracebackContext(conversation, activeReportId, userInput));

        Map<String, Object> policy = new LinkedHashMap<String, Object>();
        policy.put("max_recent_turns", RECENT_MESSAGE_LIMIT);
        policy.put("max_context_chars", MAX_CONTEXT_CHARS);
        policy.put("compression_enabled", true);
        policy.put("fact_source", "mysql");
        bundle.put("context_policy", policy);
        return bundle;
    }

    private List<Map<String, Object>> recentMessagesContext(Long conversationId, Long userId) {
        List<AgentMessageEntity> latest =
                messageRepository.findTop6ByConversationIdAndUserIdOrderByCreatedAtDesc(conversationId, userId);
        Collections.reverse(latest);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (AgentMessageEntity message : latest) {
            result.add(toMessageContext(message, 1600));
        }
        return result;
    }

    private Map<String, Object> toMessageContext(AgentMessageEntity message, int maxContentLength) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("message_id", message.id);
        item.put("external_message_id", message.externalMessageId);
        item.put("role", message.role);
        item.put("content", truncate(message.content, maxContentLength));
        item.put("content_type", message.contentType);
        item.put("image_file_id", message.imageFileId);
        item.put("report_id", message.reportId);
        Map<String, Object> metadata = safeMap(parseJson(message.metadataJson));
        item.put("metadata", metadata);
        copyMetadataField(item, metadata, "node_name");
        copyMetadataField(item, metadata, "route_target");
        copyMetadataField(item, metadata, "answer_type");
        copyMetadataField(item, metadata, "rag_query");
        Map<String, Object> structuredContent = safeMap(metadata.get("structured_content"));
        if (!structuredContent.isEmpty()) {
            item.put("structured_content", structuredContent);
        }
        item.put("created_at", message.createdAt == null ? null : message.createdAt.toString());
        return item;
    }

    private Map<String, Object> lastFinalAnswerContext(Long conversationId, Long userId) {
        List<AgentMessageEntity> latest =
                messageRepository.findTop20ByConversationIdAndUserIdOrderByCreatedAtDesc(
                        conversationId,
                        userId
                );
        for (AgentMessageEntity message : latest) {
            if (!"assistant".equals(message.role) || !StringUtils.hasText(message.content)) {
                continue;
            }
            return toMessageContext(message, 2400);
        }
        return Collections.emptyMap();
    }

    private Map<String, Object> activeReportContext(Long reportId, Long userId) {
        if (reportId == null) {
            return null;
        }
        Optional<TongueReportEntity> optional = reportRepository.findByIdAndUserIdAndDeletedAtIsNull(reportId, userId);
        if (!optional.isPresent()) {
            return null;
        }
        TongueReportEntity report = optional.get();
        Map<String, Object> context = new LinkedHashMap<String, Object>();
        context.put("report_id", report.id);
        context.put("task_id", report.taskId);
        context.put("image_file_id", report.imageFileId);
        context.put("thread_id", report.threadId);
        context.put("report_status", report.reportStatus);
        context.put("summary", truncate(report.summary, 3000));
        context.put("feature_summary", truncate(report.featureSummary, 1200));
        context.put("detected_feature_codes", parseJson(report.detectedFeatureCodes));
        context.put("standard_features", parseJson(report.standardFeaturesJson));
        context.put("rag_query", truncate(report.ragQuery, 1000));
        context.put("rag_grounded", report.ragGrounded);
        context.put("rag_evidence", evidenceContext(report.id, false));
        context.put("structured_answer", extractStructuredContentFromDraftReport(report.draftReportJson));
        context.put("created_at", report.createdAt == null ? null : report.createdAt.toString());
        return context;
    }

    private List<Map<String, Object>> evidenceContext(Long reportId, boolean includeContent) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        List<TongueReportEvidenceEntity> evidenceList = evidenceRepository.findByReportId(reportId);
        int limit = Math.min(5, evidenceList.size());
        for (int i = 0; i < limit; i++) {
            TongueReportEvidenceEntity evidence = evidenceList.get(i);
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("evidence_id", evidence.id);
            item.put("chunk_id", evidence.chunkId);
            item.put("doc_id", evidence.docId);
            item.put("title", evidence.title);
            item.put("source_uri", evidence.sourceUri);
            item.put("final_score", evidence.finalScore);
            if (includeContent) {
                item.put("content", truncate(evidence.content, 1000));
            }
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> tracebackContext(
            AgentConversationEntity conversation,
            Long activeReportId,
            String userInput
    ) {
        if (!needsTraceback(userInput)) {
            return Collections.emptyMap();
        }
        Map<String, Object> context = new LinkedHashMap<String, Object>();
        List<AgentMessageEntity> latest =
                messageRepository.findTop20ByConversationIdAndUserIdOrderByCreatedAtDesc(
                        conversation.id,
                        conversation.userId
                );
        Collections.reverse(latest);
        List<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();
        for (AgentMessageEntity message : latest) {
            messages.add(toMessageContext(message, 900));
        }
        context.put("retrieved_messages", messages);
        if (activeReportId != null) {
            context.put("active_report_evidence", evidenceContext(activeReportId, true));
        }
        context.put("reason", "query_mentions_previous_context");
        return context;
    }

    private boolean needsTraceback(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String compact = text.replaceAll("\\s+", "");
        String[] keywords = new String[] {
                "刚才", "前面", "上次", "上一份", "上一轮", "之前",
                "哪里不舒服", "我说过", "我提到", "依据", "建议", "推荐",
                "饮食", "吃什么", "怎么吃", "忌口", "食物", "调理", "注意",
                "详细一点", "具体一点"
        };
        for (String keyword : keywords) {
            if (compact.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private AgentContextSummaryEntity compressIfNeeded(AgentConversationEntity conversation) {
        List<AgentMessageEntity> messages =
                messageRepository.findByConversationIdAndUserIdOrderByCreatedAtAsc(
                        conversation.id,
                        conversation.userId
                );
        Optional<AgentContextSummaryEntity> latestSummary =
                summaryRepository.findFirstByConversationIdAndUserIdAndStatusOrderByCreatedAtDesc(
                        conversation.id,
                        conversation.userId,
                        ACTIVE
                );
        int totalChars = totalChars(messages);
        if (messages.size() <= COMPRESS_MESSAGE_THRESHOLD && totalChars <= MAX_CONTEXT_CHARS) {
            return latestSummary.orElse(null);
        }

        Long alreadyCompressedEnd = latestSummary
                .map(summary -> summary.sourceEndMessageId)
                .orElse(null);
        int compressEndExclusive = Math.max(0, messages.size() - RECENT_MESSAGE_LIMIT);
        List<AgentMessageEntity> candidates = new ArrayList<AgentMessageEntity>();
        for (int i = 0; i < compressEndExclusive; i++) {
            AgentMessageEntity message = messages.get(i);
            if (alreadyCompressedEnd == null || message.id > alreadyCompressedEnd) {
                candidates.add(message);
            }
        }
        if (candidates.isEmpty()) {
            return latestSummary.orElse(null);
        }

        AgentContextSummaryEntity summary = new AgentContextSummaryEntity();
        summary.conversationId = conversation.id;
        summary.userId = conversation.userId;
        summary.sourceStartMessageId = candidates.get(0).id;
        summary.sourceEndMessageId = candidates.get(candidates.size() - 1).id;
        summary.compressionVersion = "context-summary-v1";
        summary.status = ACTIVE;

        SummaryMetadata metadata = collectSummaryMetadata(candidates);
        summary.sourceMessageIdsJson = writeJsonOrNull(metadata.messageIds);
        summary.sourceReportIdsJson = writeJsonOrNull(metadata.reportIds);
        summary.sourceFileIdsJson = writeJsonOrNull(metadata.fileIds);
        summary.sourceEvidenceIdsJson = writeJsonOrNull(metadata.evidenceIds);
        summary.summaryText = buildSummaryText(latestSummary.orElse(null), candidates, metadata);
        summary.structuredSummaryJson = writeJsonOrNull(buildStructuredSummary(
                latestSummary.orElse(null),
                candidates,
                metadata
        ));

        AgentContextSummaryEntity saved = summaryRepository.save(summary);
        conversation.summaryId = saved.id;
        conversationRepository.save(conversation);
        return saved;
    }

    private SummaryMetadata collectSummaryMetadata(List<AgentMessageEntity> candidates) {
        SummaryMetadata metadata = new SummaryMetadata();
        for (AgentMessageEntity message : candidates) {
            metadata.messageIds.add(message.id);
            if (message.reportId != null) {
                metadata.reportIds.add(message.reportId);
                for (TongueReportEvidenceEntity evidence : evidenceRepository.findByReportId(message.reportId)) {
                    metadata.evidenceIds.add(evidence.id);
                }
            }
            if (message.imageFileId != null) {
                metadata.fileIds.add(message.imageFileId);
            }
        }
        return metadata;
    }

    private String buildSummaryText(
            AgentContextSummaryEntity previousSummary,
            List<AgentMessageEntity> candidates,
            SummaryMetadata metadata
    ) {
        StringBuilder builder = new StringBuilder();
        if (previousSummary != null && StringUtils.hasText(previousSummary.summaryText)) {
            builder.append(previousSummary.summaryText).append("\n");
        }
        builder.append("本段压缩摘要：");
        builder.append("用户和助手围绕舌象健康分析进行了 ").append(candidates.size()).append(" 条历史消息交流。");
        if (!metadata.fileIds.isEmpty()) {
            builder.append("已上传图片ID：").append(metadata.fileIds).append("。");
        }
        if (!metadata.reportIds.isEmpty()) {
            builder.append("已生成或讨论报告ID：").append(metadata.reportIds).append("。");
        }

        List<String> userSnippets = new ArrayList<String>();
        List<String> assistantSnippets = new ArrayList<String>();
        for (AgentMessageEntity message : candidates) {
            if (!StringUtils.hasText(message.content)) {
                continue;
            }
            String snippet = truncate(message.content.replace('\n', ' '), 180);
            if ("user".equals(message.role)) {
                userSnippets.add(snippet);
            } else if ("assistant".equals(message.role)) {
                assistantSnippets.add(snippet);
            }
        }
        if (!userSnippets.isEmpty()) {
            builder.append("用户主要问题/补充描述：").append(userSnippets).append("。");
        }
        if (!assistantSnippets.isEmpty()) {
            builder.append("已给出的解释和建议：").append(assistantSnippets).append("。");
        }
        builder.append("如果后续缺少细节，应根据 summary 元数据回查 agent_message、tongue_report 和 tongue_report_evidence。");
        return truncate(builder.toString(), 5000);
    }

    private Map<String, Object> buildStructuredSummary(
            AgentContextSummaryEntity previousSummary,
            List<AgentMessageEntity> candidates,
            SummaryMetadata metadata
    ) {
        Map<String, Object> structured = new LinkedHashMap<String, Object>();
        structured.put("schema_version", "1.0");
        structured.put("previous_summary_id", previousSummary == null ? null : previousSummary.id);
        structured.put("compressed_message_count", candidates.size());
        structured.put("user_main_questions", roleSnippets(candidates, "user"));
        structured.put("assistant_answers", roleSnippets(candidates, "assistant"));
        structured.put("uploaded_file_ids", metadata.fileIds);
        structured.put("report_ids", metadata.reportIds);
        structured.put("evidence_ids", metadata.evidenceIds);
        structured.put("source_message_ids", metadata.messageIds);
        structured.put("missing_detail_policy", "query_mysql_by_source_ids");
        return structured;
    }

    private List<String> roleSnippets(List<AgentMessageEntity> messages, String role) {
        List<String> snippets = new ArrayList<String>();
        for (AgentMessageEntity message : messages) {
            if (role.equals(message.role) && StringUtils.hasText(message.content)) {
                snippets.add(truncate(message.content.replace('\n', ' '), 220));
            }
        }
        return snippets;
    }

    private Map<String, Object> toSummaryContext(AgentContextSummaryEntity summary) {
        if (summary == null) {
            return null;
        }
        Map<String, Object> context = new LinkedHashMap<String, Object>();
        context.put("summary_id", summary.id);
        context.put("text", summary.summaryText);
        context.put("structured_summary", parseJson(summary.structuredSummaryJson));
        List<Long> range = new ArrayList<Long>();
        range.add(summary.sourceStartMessageId);
        range.add(summary.sourceEndMessageId);
        context.put("source_message_range", range);
        context.put("source_message_ids", parseJson(summary.sourceMessageIdsJson));
        context.put("source_report_ids", parseJson(summary.sourceReportIdsJson));
        context.put("source_file_ids", parseJson(summary.sourceFileIdsJson));
        context.put("source_evidence_ids", parseJson(summary.sourceEvidenceIdsJson));
        context.put("compression_version", summary.compressionVersion);
        return context;
    }

    private int totalChars(List<AgentMessageEntity> messages) {
        int total = 0;
        for (AgentMessageEntity message : messages) {
            if (message.content != null) {
                total += message.content.length();
            }
        }
        return total;
    }

    private Long parseConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return null;
        }
        try {
            return Long.valueOf(conversationId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Object parseJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Object>() {
            });
        } catch (Exception ex) {
            return json;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    private void copyMetadataField(
            Map<String, Object> target,
            Map<String, Object> metadata,
            String key
    ) {
        Object value = metadata.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    private Map<String, Object> extractStructuredContentFromDraftReport(String draftReportJson) {
        Map<String, Object> draftReport = safeMap(parseJson(draftReportJson));
        Map<String, Object> metadata = safeMap(draftReport.get("metadata"));
        Map<String, Object> structuredAnswer = safeMap(metadata.get("structured_answer"));
        if (!structuredAnswer.isEmpty()) {
            return structuredAnswer;
        }
        return safeMap(draftReport.get("structured_answer"));
    }

    private String writeJsonOrNull(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("write context json failed", ex);
        }
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

    private static class SummaryMetadata {
        private final List<Long> messageIds = new ArrayList<Long>();
        private List<Long> reportIds = new ArrayList<Long>();
        private List<Long> fileIds = new ArrayList<Long>();
        private List<Long> evidenceIds = new ArrayList<Long>();
    }
}

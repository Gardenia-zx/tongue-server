package com.tongue.server.agent.context.repository;

import com.tongue.server.agent.context.entity.AgentChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("agentChatV2MessageRepository")
public interface AgentChatMessageRepository extends JpaRepository<AgentChatMessageEntity, Long> {
    List<AgentChatMessageEntity> findByUserIdAndConversationIdOrderBySequenceNoDesc(
            Long userId,
            String conversationId,
            Pageable pageable
    );

    Optional<AgentChatMessageEntity> findFirstByUserIdAndConversationIdAndRoleAndReportIdIsNotNullOrderBySequenceNoDesc(
            Long userId,
            String conversationId,
            String role
    );

    Optional<AgentChatMessageEntity> findFirstByUserIdAndReportIdOrderBySequenceNoDesc(Long userId, Long reportId);

    long countByUserIdAndConversationId(Long userId, String conversationId);

    boolean existsByMessageId(String messageId);
}

package com.tongue.server.agent.context.repository;

import com.tongue.server.agent.context.entity.AgentChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("agentChatV2MessageRepository")
public interface AgentChatMessageRepository extends JpaRepository<AgentChatMessageEntity, Long> {
    List<AgentChatMessageEntity> findByUserIdAndConversationIdOrderBySequenceNoDesc(
            Long userId,
            String conversationId,
            Pageable pageable
    );

    long countByUserIdAndConversationId(Long userId, String conversationId);

    boolean existsByMessageId(String messageId);
}

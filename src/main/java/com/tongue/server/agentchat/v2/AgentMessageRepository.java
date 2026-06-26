package com.tongue.server.agentchat.v2;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("agentChatV2MessageRepository")
public interface AgentMessageRepository extends JpaRepository<AgentMessageEntity, Long> {
    List<AgentMessageEntity> findByUserIdAndConversationIdOrderBySequenceNoDesc(
            Long userId,
            String conversationId,
            Pageable pageable
    );

    long countByUserIdAndConversationId(Long userId, String conversationId);

    boolean existsByMessageId(String messageId);
}

package com.tongue.server.agentchat.v2;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentMessageRepository extends JpaRepository<AgentMessageEntity, Long> {
    List<AgentMessageEntity> findByUserIdAndConversationIdOrderBySequenceNoDesc(
            Long userId,
            String conversationId,
            Pageable pageable
    );

    long countByUserIdAndConversationId(Long userId, String conversationId);
}

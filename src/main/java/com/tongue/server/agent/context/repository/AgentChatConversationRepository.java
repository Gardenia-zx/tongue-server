package com.tongue.server.agent.context.repository;

import com.tongue.server.agent.context.entity.AgentChatConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository("agentChatV2ConversationRepository")
public interface AgentChatConversationRepository extends JpaRepository<AgentChatConversationEntity, Long> {
    Optional<AgentChatConversationEntity> findByUserIdAndConversationId(Long userId, String conversationId);
}

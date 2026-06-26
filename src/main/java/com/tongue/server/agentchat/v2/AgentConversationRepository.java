package com.tongue.server.agentchat.v2;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository("agentChatV2ConversationRepository")
public interface AgentConversationRepository extends JpaRepository<AgentConversationEntity, Long> {
    Optional<AgentConversationEntity> findByUserIdAndConversationId(Long userId, String conversationId);
}

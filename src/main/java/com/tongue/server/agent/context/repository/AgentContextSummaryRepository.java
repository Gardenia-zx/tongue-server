package com.tongue.server.agent.context.repository;

import com.tongue.server.agent.context.entity.AgentContextSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentContextSummaryRepository extends JpaRepository<AgentContextSummaryEntity, Long> {

    Optional<AgentContextSummaryEntity> findFirstByConversationIdAndUserIdAndStatusOrderByCreatedAtDesc(
            Long conversationId,
            Long userId,
            String status
    );
}

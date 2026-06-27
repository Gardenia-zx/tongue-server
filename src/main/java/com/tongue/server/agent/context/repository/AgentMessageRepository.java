package com.tongue.server.agent.context.repository;

import com.tongue.server.agent.context.entity.AgentMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentMessageRepository extends JpaRepository<AgentMessageEntity, Long> {

    List<AgentMessageEntity> findByConversationIdAndUserIdOrderByCreatedAtAsc(Long conversationId, Long userId);

    List<AgentMessageEntity> findTop6ByConversationIdAndUserIdOrderByCreatedAtDesc(Long conversationId, Long userId);

    List<AgentMessageEntity> findTop20ByConversationIdAndUserIdOrderByCreatedAtDesc(Long conversationId, Long userId);

    Optional<AgentMessageEntity> findFirstByUserIdAndReportIdOrderByCreatedAtDesc(Long userId, Long reportId);

    Optional<AgentMessageEntity> findFirstByConversationIdAndUserIdAndExternalMessageId(
            Long conversationId,
            Long userId,
            String externalMessageId
    );
}

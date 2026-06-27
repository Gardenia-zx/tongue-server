package com.tongue.server.agent.context.repository;

import com.tongue.server.agent.context.entity.AgentChatTurnEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository("agentChatV2TurnRepository")
public interface AgentChatTurnRepository extends JpaRepository<AgentChatTurnEntity, Long> {
    Optional<AgentChatTurnEntity> findByUserIdAndRequestId(Long userId, String requestId);
}

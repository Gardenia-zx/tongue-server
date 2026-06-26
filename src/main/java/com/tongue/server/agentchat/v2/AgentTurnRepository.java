package com.tongue.server.agentchat.v2;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository("agentChatV2TurnRepository")
public interface AgentTurnRepository extends JpaRepository<AgentTurnEntity, Long> {
    Optional<AgentTurnEntity> findByUserIdAndRequestId(Long userId, String requestId);
}

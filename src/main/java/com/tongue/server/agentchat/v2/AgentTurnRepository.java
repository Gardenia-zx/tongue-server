package com.tongue.server.agentchat.v2;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentTurnRepository extends JpaRepository<AgentTurnEntity, Long> {
    Optional<AgentTurnEntity> findByUserIdAndRequestId(Long userId, String requestId);
}

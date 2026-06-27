package com.tongue.server.agent.context.repository;

import com.tongue.server.agent.context.entity.AgentMemoryOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentMemoryOutboxRepository extends JpaRepository<AgentMemoryOutboxEntity, Long> {

    Optional<AgentMemoryOutboxEntity> findByEventId(String eventId);
}

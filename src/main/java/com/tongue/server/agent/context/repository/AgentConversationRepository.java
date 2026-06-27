package com.tongue.server.agent.context.repository;

import com.tongue.server.agent.context.entity.AgentConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface AgentConversationRepository extends JpaRepository<AgentConversationEntity, Long> {

    Optional<AgentConversationEntity> findByIdAndUserIdAndStatus(Long id, Long userId, String status);

    Optional<AgentConversationEntity> findFirstByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, String status);

    List<AgentConversationEntity> findByUserIdAndActiveReportIdAndStatus(Long userId, Long activeReportId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AgentConversationEntity c where c.id = :id and c.userId = :userId and c.status = :status")
    Optional<AgentConversationEntity> findForUpdate(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("status") String status
    );
}

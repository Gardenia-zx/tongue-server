package com.tongue.server.health.repository;

import com.tongue.server.health.entity.UserHealthPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserHealthPlanRepository extends JpaRepository<UserHealthPlanEntity, Long> {
    Optional<UserHealthPlanEntity> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    List<UserHealthPlanEntity> findByUserIdAndStatus(Long userId, String status);

    Optional<UserHealthPlanEntity> findByIdAndUserId(Long id, Long userId);
}

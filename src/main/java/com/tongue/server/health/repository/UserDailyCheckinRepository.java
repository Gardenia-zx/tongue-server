package com.tongue.server.health.repository;

import com.tongue.server.health.entity.UserDailyCheckinEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserDailyCheckinRepository extends JpaRepository<UserDailyCheckinEntity, Long> {
    Optional<UserDailyCheckinEntity> findByUserIdAndCheckinDate(Long userId, LocalDate checkinDate);

    List<UserDailyCheckinEntity> findByUserIdAndCheckinDateBetweenOrderByCheckinDateDesc(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );

    List<UserDailyCheckinEntity> findByUserIdAndPlanIdOrderByCheckinDateAsc(Long userId, Long planId);
}

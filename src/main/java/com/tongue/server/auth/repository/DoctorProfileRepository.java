package com.tongue.server.auth.repository;

import com.tongue.server.auth.entity.DoctorProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfileEntity, Long> {
    Optional<DoctorProfileEntity> findByUserId(Long userId);

    List<DoctorProfileEntity> findByReviewStatusOrderByCreatedAtDesc(String reviewStatus);
}

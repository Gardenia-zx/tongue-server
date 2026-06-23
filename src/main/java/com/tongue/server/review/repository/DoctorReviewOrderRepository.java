package com.tongue.server.review.repository;

import com.tongue.server.review.entity.DoctorReviewOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DoctorReviewOrderRepository extends JpaRepository<DoctorReviewOrderEntity, Long> {
    List<DoctorReviewOrderEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<DoctorReviewOrderEntity> findByDoctorUserIdOrderByCreatedAtDesc(Long doctorUserId);

    List<DoctorReviewOrderEntity> findByStatusOrderByCreatedAtAsc(String status);
}

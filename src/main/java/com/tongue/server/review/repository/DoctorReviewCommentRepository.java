package com.tongue.server.review.repository;

import com.tongue.server.review.entity.DoctorReviewCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DoctorReviewCommentRepository extends JpaRepository<DoctorReviewCommentEntity, Long> {
    List<DoctorReviewCommentEntity> findByReviewOrderIdOrderByCreatedAtAsc(Long reviewOrderId);
}

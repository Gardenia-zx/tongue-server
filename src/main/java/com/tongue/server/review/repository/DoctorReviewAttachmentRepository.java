package com.tongue.server.review.repository;

import com.tongue.server.review.entity.DoctorReviewAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorReviewAttachmentRepository extends JpaRepository<DoctorReviewAttachmentEntity, Long> {
}

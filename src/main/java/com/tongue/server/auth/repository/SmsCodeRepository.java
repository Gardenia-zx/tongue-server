package com.tongue.server.auth.repository;

import com.tongue.server.auth.entity.SmsCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SmsCodeRepository extends JpaRepository<SmsCodeEntity, Long> {
    Optional<SmsCodeEntity> findFirstByPhoneAndSceneOrderByCreatedAtDesc(String phone, String scene);
}

package com.tongue.server.auth.repository;

import com.tongue.server.auth.entity.UserTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserTokenRepository extends JpaRepository<UserTokenEntity, Long> {
    Optional<UserTokenEntity> findByTokenHashAndRevokedAtIsNull(String tokenHash);
}

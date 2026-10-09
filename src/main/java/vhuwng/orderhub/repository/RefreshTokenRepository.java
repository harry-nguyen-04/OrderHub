package vhuwng.orderhub.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vhuwng.orderhub.entity.RefreshTokenEntity;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
}

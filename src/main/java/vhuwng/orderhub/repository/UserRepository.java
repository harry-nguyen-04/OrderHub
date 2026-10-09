package vhuwng.orderhub.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vhuwng.orderhub.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    boolean existsByUsername(String username);

    Optional<UserEntity> findByUsername(String username);
}

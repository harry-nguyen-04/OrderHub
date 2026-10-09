package vhuwng.orderhub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vhuwng.orderhub.entity.IdempotencyKeyEntity;
import vhuwng.orderhub.entity.IdempotencyOperation;

import java.util.Optional;

public interface IdempotencyRepository extends JpaRepository<IdempotencyKeyEntity, Long> {
    Optional<IdempotencyKeyEntity> findByUserIdAndOperationAndKey(Long userId, IdempotencyOperation operation, String key);
}

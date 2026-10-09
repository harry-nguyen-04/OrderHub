package vhuwng.orderhub.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vhuwng.orderhub.entity.PaymentEntity;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
    Optional<PaymentEntity> findByOrderId(Long orderId);
}

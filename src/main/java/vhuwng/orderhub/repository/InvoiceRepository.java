package vhuwng.orderhub.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import vhuwng.orderhub.entity.InvoiceEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, Long> {
    
    @Query("SELECT i FROM InvoiceEntity i WHERE i.order.id = :orderId ORDER BY i.uploadedAt DESC")
    List<InvoiceEntity> findByOrderId(@Param("orderId") Long orderId);
}

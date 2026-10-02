package vhuwng.orderhub.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import vhuwng.orderhub.entity.ProductEntity;

public interface ProductRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {
    List<ProductEntity> findAllByIsActiveTrue();
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, Long id);
    
}

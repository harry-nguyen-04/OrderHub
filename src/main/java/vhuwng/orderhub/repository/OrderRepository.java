package vhuwng.orderhub.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vhuwng.orderhub.entity.OrderEntity;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    boolean existsByCode(String code);

    Page<OrderEntity> findByUserId(Long userId, Pageable pageable);

    @Query("""
            select distinct o from OrderEntity o
            left join fetch o.items i
            left join fetch i.product
            left join fetch o.user
            where o.id = :id
            """)
    Optional<OrderEntity> findWithDetailsById(@Param("id") Long id);

    @Query("""
            select distinct o from OrderEntity o
            left join fetch o.items i
            left join fetch i.product
            left join fetch o.user
            where o.id in :ids
            """)
    List<OrderEntity> findWithDetailsByIdIn(@Param("ids") Collection<Long> ids);
}

package vhuwng.orderhub.repository.specification;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import vhuwng.orderhub.dto.filter.ProductFilterDto;
import vhuwng.orderhub.entity.ProductEntity;

public class ProductSpecifications {
    public static Specification<ProductEntity> filter(ProductFilterDto filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.sku() != null) {
                predicates.add(cb.equal(root.get("sku"), filter.sku()));
            }
            if (filter.name() != null && !filter.name().isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("name")),
                        "%" + filter.name().toLowerCase() + "%"));
            }
            if (filter.category() != null) {
                predicates.add(cb.equal(root.get("category"), filter.category()));
            }
            if (filter.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("unitPrice"), filter.minPrice()));
            }
            if (filter.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("unitPrice"), filter.maxPrice()));
            }
            if (filter.currency() != null) {
                predicates.add(cb.equal(root.get("currency"), filter.currency()));
            }
            if (filter.sortBy() != null) {
                if (filter.sortOrder().equalsIgnoreCase("asc")) {
                    query.orderBy(cb.asc(root.get(filter.sortBy())));
                } else {
                    query.orderBy(cb.desc(root.get(filter.sortBy())));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
    
}

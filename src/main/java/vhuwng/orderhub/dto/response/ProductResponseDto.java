package vhuwng.orderhub.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import vhuwng.orderhub.entity.ProductEntity;

public record ProductResponseDto(
        Long id,
        String sku,
        String name,
        String category,
        BigDecimal unitPrice,
        String currency,
        Integer stock,
        Boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductResponseDto fromEntity(ProductEntity product) {
        return new ProductResponseDto(
            product.getId(),
            product.getSku(),
            product.getName(),
            product.getCategory(),
            product.getUnitPrice(),
            product.getCurrency(),
            product.getStock(),
            product.getIsActive(),
            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }
}

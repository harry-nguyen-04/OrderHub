package vhuwng.orderhub.dto.response;

import java.math.BigDecimal;

import vhuwng.orderhub.entity.OrderItemEntity;

public record OrderItemResponseDto(
        Long id,
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineAmount
) {
    public static OrderItemResponseDto fromEntity(OrderItemEntity item) {
        BigDecimal lineAmount = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new OrderItemResponseDto(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                lineAmount
        );
    }
}

package vhuwng.orderhub.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import vhuwng.orderhub.entity.OrderEntity;

public record CreateOrderResponseDto(
        Long id,
        String code,
        String status,
        BigDecimal totalAmount,
        Long userId,
        String username,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemResponseDto> items
) {
    public static CreateOrderResponseDto fromEntity(OrderEntity order) {
        OrderResponseDto response = OrderResponseDto.fromEntity(order);
        return new CreateOrderResponseDto(
                response.id(),
                response.code(),
                response.status(),
                response.totalAmount(),
                response.userId(),
                response.username(),
                response.createdAt(),
                response.updatedAt(),
                response.items()
        );
    }
}

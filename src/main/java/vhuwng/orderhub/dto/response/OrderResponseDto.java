package vhuwng.orderhub.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import vhuwng.orderhub.entity.OrderEntity;

public record OrderResponseDto(
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
    public static OrderResponseDto fromEntity(OrderEntity order) {
        List<OrderItemResponseDto> items = order.getItems().stream()
                .map(OrderItemResponseDto::fromEntity)
                .toList();
        return new OrderResponseDto(
                order.getId(),
                order.getCode(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getUser().getId(),
                order.getUser().getUsername(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items
        );
    }
}

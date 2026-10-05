package vhuwng.orderhub.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record CreateOrderRequestDto(
        @NotEmpty(message = "Order must contain at least one item")
        @Valid
        List<CreateOrderItemRequestDto> items
) {
}

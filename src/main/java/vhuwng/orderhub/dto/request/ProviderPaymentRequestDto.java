package vhuwng.orderhub.dto.request;

import java.math.BigDecimal;

public record ProviderPaymentRequestDto(Long orderId, BigDecimal amount) {
}

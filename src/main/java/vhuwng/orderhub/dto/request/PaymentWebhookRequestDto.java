package vhuwng.orderhub.dto.request;

import java.math.BigDecimal;

public record PaymentWebhookRequestDto(
        Long orderId,
        String providerRef,
        BigDecimal amount,
        String paymentStatus
) {
}

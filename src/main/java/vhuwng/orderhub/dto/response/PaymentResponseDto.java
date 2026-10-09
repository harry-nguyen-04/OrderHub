package vhuwng.orderhub.dto.response;

public record PaymentResponseDto(
        Long orderId,
        String orderStatus,
        String paymentStatus,
        String providerRef
) {
}

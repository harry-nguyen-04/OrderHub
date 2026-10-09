package vhuwng.orderhub.dto.response;

public record ProviderPaymentResponseDto(Long orderId, String providerRef, String providerStatus) {
}

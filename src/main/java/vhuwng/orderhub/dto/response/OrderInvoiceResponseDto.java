package vhuwng.orderhub.dto.response;

import java.time.Instant;

import vhuwng.orderhub.entity.InvoiceEntity;

public record OrderInvoiceResponseDto(
        Long id,
        Long orderId,
        String mimeType,
        Long sizeBytes,
        Instant uploadedAt
) {
    public static OrderInvoiceResponseDto fromEntity(InvoiceEntity invoice) {
        return new OrderInvoiceResponseDto(
                invoice.getId(),
                invoice.getOrder().getId(),
                invoice.getMimeType(),
                invoice.getSizeBytes(),
                invoice.getUploadedAt()
        );
    }
}

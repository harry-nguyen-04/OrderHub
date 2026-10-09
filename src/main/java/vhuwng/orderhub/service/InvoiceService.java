package vhuwng.orderhub.service;

import org.springframework.web.multipart.MultipartFile;

import vhuwng.orderhub.dto.response.OrderInvoiceResponseDto;

public interface InvoiceService {
    OrderInvoiceResponseDto upload(Long orderId, MultipartFile file);
}

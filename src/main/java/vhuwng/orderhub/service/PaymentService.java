package vhuwng.orderhub.service;

import vhuwng.orderhub.dto.request.PaymentWebhookRequestDto;
import vhuwng.orderhub.dto.response.MessageResponseDto;
import vhuwng.orderhub.dto.response.PaymentResponseDto;

public interface PaymentService {
    PaymentResponseDto pay(Long orderId);

    MessageResponseDto handleWebhook(PaymentWebhookRequestDto request);
}

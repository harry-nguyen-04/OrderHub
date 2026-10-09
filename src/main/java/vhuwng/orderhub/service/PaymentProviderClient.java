package vhuwng.orderhub.service;

import vhuwng.orderhub.dto.request.ProviderPaymentRequestDto;
import vhuwng.orderhub.dto.response.ProviderPaymentResponseDto;

public interface PaymentProviderClient {
    ProviderPaymentResponseDto pay(ProviderPaymentRequestDto request);
}

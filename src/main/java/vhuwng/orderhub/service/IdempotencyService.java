package vhuwng.orderhub.service;

import vhuwng.orderhub.dto.request.CreateOrderRequestDto;
import vhuwng.orderhub.dto.response.CreateOrderResponseDto;
import vhuwng.orderhub.dto.response.IdempotencyResultDto;

public interface IdempotencyService {
    IdempotencyResultDto<CreateOrderResponseDto> createOrder(String key, CreateOrderRequestDto request);
}

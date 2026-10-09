package vhuwng.orderhub.service;

import org.springframework.data.domain.Page;

import vhuwng.orderhub.dto.request.CreateOrderRequestDto;
import vhuwng.orderhub.dto.response.CreateOrderResponseDto;
import vhuwng.orderhub.dto.response.OrderResponseDto;

public interface OrderService {
    CreateOrderResponseDto createOrder(CreateOrderRequestDto request);

    Page<OrderResponseDto> getOrders(int page, int size);

    OrderResponseDto getOrderById(Long id);
}

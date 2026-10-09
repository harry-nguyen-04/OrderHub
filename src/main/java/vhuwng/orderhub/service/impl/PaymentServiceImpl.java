package vhuwng.orderhub.service.impl;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import vhuwng.orderhub.dto.request.PaymentWebhookRequestDto;
import vhuwng.orderhub.dto.request.ProviderPaymentRequestDto;
import vhuwng.orderhub.dto.response.MessageResponseDto;
import vhuwng.orderhub.dto.response.PaymentResponseDto;
import vhuwng.orderhub.dto.response.ProviderPaymentResponseDto;
import vhuwng.orderhub.entity.OrderEntity;
import vhuwng.orderhub.entity.OrderStatus;
import vhuwng.orderhub.entity.PaymentEntity;
import vhuwng.orderhub.entity.PaymentStatus;
import vhuwng.orderhub.middleware.exception.InvalidPaymentWebhookException;
import vhuwng.orderhub.middleware.exception.PaymentConflictException;
import vhuwng.orderhub.middleware.exception.ResourceNotFoundException;
import vhuwng.orderhub.repository.OrderRepository;
import vhuwng.orderhub.repository.PaymentRepository;
import vhuwng.orderhub.service.AuthService;
import vhuwng.orderhub.service.PaymentProviderClient;
import vhuwng.orderhub.service.PaymentService;

@Service
public class PaymentServiceImpl implements PaymentService {
    private static final String PAID = "PAID";

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final AuthService authService;
    private final PaymentProviderClient providerClient;
    private final TransactionTemplate transactionTemplate;

    public PaymentServiceImpl(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            AuthService authService,
            PaymentProviderClient providerClient,
            PlatformTransactionManager transactionManager
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.authService = authService;
        this.providerClient = providerClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public PaymentResponseDto pay(Long orderId) {
        Long currentUserId = authService.getCurrentUser().id();
        PaymentSnapshot initial = transactionTemplate.execute(status -> preparePayment(orderId, currentUserId));
        if (initial == null) {
            throw new IllegalStateException("Unable to prepare payment");
        }
        if (PAID.equals(initial.paymentStatus())) {
            return initial.toResponse();
        }

        ProviderPaymentResponseDto providerResponse = providerClient.pay(
                new ProviderPaymentRequestDto(orderId, initial.amount()));
        PaymentSnapshot updated = transactionTemplate.execute(status -> recordProviderResponse(orderId, providerResponse));
        if (updated == null) {
            throw new IllegalStateException("Unable to record payment provider response");
        }
        return updated.toResponse();
    }

    private PaymentSnapshot preparePayment(Long orderId, Long currentUserId) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));
        if (!order.getUser().getId().equals(currentUserId)) {
            throw new ResourceNotFoundException("Order with id " + orderId + " not found");
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.COMPLETED) {
            throw new PaymentConflictException("Order cannot be paid in its current status");
        }

        PaymentEntity payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (order.getStatus() == OrderStatus.PAID) {
            if (payment == null || payment.getStatus() != PaymentStatus.PAID) {
                throw new PaymentConflictException("Order payment status is inconsistent");
            }
            return snapshot(order, payment);
        }

        if (payment == null) {
            payment = new PaymentEntity();
            payment.setOrder(order);
            payment.setAmount(order.getTotalAmount());
            payment.setStatus(PaymentStatus.PENDING);
            payment = paymentRepository.saveAndFlush(payment);
        } else if (payment.getStatus() == PaymentStatus.PAID) {
            throw new PaymentConflictException("Payment status is inconsistent with order status");
        } else if (payment.getAmount().compareTo(order.getTotalAmount()) != 0) {
            throw new PaymentConflictException("Payment amount does not match order total");
        }
        return snapshot(order, payment);
    }

    private PaymentSnapshot recordProviderResponse(Long orderId, ProviderPaymentResponseDto providerResponse) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));
        PaymentEntity payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment for order " + orderId + " not found"));
        if (payment.getProviderRef() != null && !payment.getProviderRef().equals(providerResponse.providerRef())) {
            throw new PaymentConflictException("Payment provider reference does not match");
        }
        payment.setProviderRef(providerResponse.providerRef());
        paymentRepository.save(payment);
        return snapshot(order, payment);
    }

    @Override
    @Transactional
    public MessageResponseDto handleWebhook(PaymentWebhookRequestDto request) {
        if (request == null || request.orderId() == null || request.amount() == null
                || request.providerRef() == null || request.providerRef().isBlank()
                || !PAID.equals(request.paymentStatus())) {
            throw new InvalidPaymentWebhookException("Invalid payment webhook fields");
        }

        OrderEntity order = orderRepository.findByIdForUpdate(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order with id " + request.orderId() + " not found"));
        PaymentEntity payment = paymentRepository.findByOrderId(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment for order " + request.orderId() + " not found"));
        if (payment.getAmount().compareTo(request.amount()) != 0) {
            throw new PaymentConflictException("Payment webhook amount does not match");
        }
        if (payment.getProviderRef() != null && !payment.getProviderRef().equals(request.providerRef())) {
            throw new PaymentConflictException("Payment webhook provider reference does not match");
        }

        if (payment.getStatus() == PaymentStatus.PAID && order.getStatus() == OrderStatus.PAID) {
            return new MessageResponseDto("Webhook received");
        }
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PAID) {
            throw new PaymentConflictException("Order cannot be paid in its current status");
        }

        payment.setProviderRef(request.providerRef());
        payment.setStatus(PaymentStatus.PAID);
        if (payment.getPaidAt() == null) {
            payment.setPaidAt(Instant.now());
        }
        order.setStatus(OrderStatus.PAID);
        paymentRepository.save(payment);
        orderRepository.save(order);
        return new MessageResponseDto("Webhook received");
    }

    private PaymentSnapshot snapshot(OrderEntity order, PaymentEntity payment) {
        return new PaymentSnapshot(
                order.getId(),
                order.getStatus().name(),
                payment.getStatus().name(),
                payment.getProviderRef(),
                payment.getAmount()
        );
    }

    private record PaymentSnapshot(
            Long orderId,
            String orderStatus,
            String paymentStatus,
            String providerRef,
            BigDecimal amount
    ) {
        private PaymentResponseDto toResponse() {
            return new PaymentResponseDto(orderId, orderStatus, paymentStatus, providerRef);
        }
    }
}

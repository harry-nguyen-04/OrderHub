package vhuwng.orderhub.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import vhuwng.orderhub.dto.request.CreateOrderRequestDto;
import vhuwng.orderhub.dto.response.CreateOrderResponseDto;
import vhuwng.orderhub.dto.response.IdempotencyResultDto;
import vhuwng.orderhub.dto.response.OrderInvoiceResponseDto;
import vhuwng.orderhub.dto.response.OrderResponseDto;
import vhuwng.orderhub.dto.response.PaymentResponseDto;
import vhuwng.orderhub.service.IdempotencyService;
import vhuwng.orderhub.service.InvoiceService;
import vhuwng.orderhub.service.OrderService;
import vhuwng.orderhub.service.PaymentService;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;
    private final IdempotencyService idempotencyService;
    private final PaymentService paymentService;
    private final InvoiceService invoiceService;

    public OrderController(
            OrderService orderService,
            IdempotencyService idempotencyService,
            PaymentService paymentService,
            InvoiceService invoiceService
    ) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
        this.paymentService = paymentService;
        this.invoiceService = invoiceService;
    }

    @PostMapping
    public ResponseEntity<CreateOrderResponseDto> createOrder(
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CreateOrderRequestDto request
    ) {
        IdempotencyResultDto<CreateOrderResponseDto> result = idempotencyService.createOrder(key, request);
        return ResponseEntity.status(result.responseStatus())
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.body());
    }

    @GetMapping
    public ResponseEntity<Page<OrderResponseDto>> getOrders(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size
    ) {
        return ResponseEntity.ok(orderService.getOrders(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDto> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<PaymentResponseDto> payOrder(@PathVariable Long id) {
        var response = paymentService.pay(id);
        if ("PAID".equals(response.paymentStatus())) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping(value = "/{id}/invoice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OrderInvoiceResponseDto> uploadInvoice(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invoiceService.upload(id, file));
    }
}

package vhuwng.orderhub.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.ObjectMapper;
import vhuwng.orderhub.dto.request.PaymentWebhookRequestDto;
import vhuwng.orderhub.dto.response.MessageResponseDto;
import vhuwng.orderhub.middleware.exception.InvalidPaymentWebhookException;
import vhuwng.orderhub.security.PaymentWebhookSignatureVerifier;
import vhuwng.orderhub.service.PaymentService;

@RestController
@RequestMapping("/api/webhooks/payment")
public class PaymentWebhookController {
    private static final String SIGNATURE_HEADER = "X-Payment-Signature";

    private final ObjectMapper objectMapper;
    private final PaymentWebhookSignatureVerifier signatureVerifier;
    private final PaymentService paymentService;

    public PaymentWebhookController(
            ObjectMapper objectMapper,
            PaymentWebhookSignatureVerifier signatureVerifier,
            PaymentService paymentService
    ) {
        this.objectMapper = objectMapper;
        this.signatureVerifier = signatureVerifier;
        this.paymentService = paymentService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponseDto> receive(
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody byte[] rawBody
    ) {
        signatureVerifier.verify(rawBody, signature);
        PaymentWebhookRequestDto request;
        try {
            request = objectMapper.readValue(rawBody, PaymentWebhookRequestDto.class);
        } catch (Exception ex) {
            throw new InvalidPaymentWebhookException("Invalid payment webhook body");
        }
        return ResponseEntity.ok(paymentService.handleWebhook(request));
    }
}

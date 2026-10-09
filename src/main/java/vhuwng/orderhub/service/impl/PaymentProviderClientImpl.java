package vhuwng.orderhub.service.impl;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import vhuwng.orderhub.dto.request.ProviderPaymentRequestDto;
import vhuwng.orderhub.dto.response.ProviderPaymentResponseDto;
import vhuwng.orderhub.middleware.exception.PaymentProviderException;
import vhuwng.orderhub.service.PaymentProviderClient;

@Service
public class PaymentProviderClientImpl implements PaymentProviderClient {
    private final RestClient restClient;
    private final Retry retry;
    private final TimeLimiter timeLimiter;
    private final ExecutorService executor;

    public PaymentProviderClientImpl(
            RestClient paymentProviderRestClient,
            Retry paymentProviderRetry,
            TimeLimiter paymentProviderTimeLimiter,
            ExecutorService paymentProviderExecutor
    ) {
        this.restClient = paymentProviderRestClient;
        this.retry = paymentProviderRetry;
        this.timeLimiter = paymentProviderTimeLimiter;
        this.executor = paymentProviderExecutor;
    }

    @Override
    public ProviderPaymentResponseDto pay(ProviderPaymentRequestDto request) {
        Supplier<ProviderPaymentResponseDto> retryingCall = Retry.decorateSupplier(retry, () -> callProvider(request));
        Future<ProviderPaymentResponseDto> future;
        try {
            future = executor.submit(retryingCall::get);
            return timeLimiter.executeFutureSupplier(() -> future);
        } catch (TimeoutException ex) {
            throw new PaymentProviderException(HttpStatus.GATEWAY_TIMEOUT, "Payment provider timed out");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PaymentProviderException(HttpStatus.SERVICE_UNAVAILABLE, "Payment provider request interrupted");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw translate(runtimeException);
            }
            throw new PaymentProviderException(HttpStatus.SERVICE_UNAVAILABLE, "Payment provider is unavailable");
        } catch (PaymentProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PaymentProviderException(HttpStatus.SERVICE_UNAVAILABLE, "Payment provider is unavailable");
        }
    }

    private ProviderPaymentResponseDto callProvider(ProviderPaymentRequestDto request) {
        ResponseEntity<ProviderPaymentResponseDto> response = restClient.post()
                .uri("/mock-payments")
                .body(request)
                .retrieve()
                .toEntity(ProviderPaymentResponseDto.class);
        ProviderPaymentResponseDto body = response.getBody();
        if (response.getStatusCode() != HttpStatus.ACCEPTED
                || body == null
                || !request.orderId().equals(body.orderId())
                || body.providerRef() == null
                || body.providerRef().isBlank()
                || !"ACCEPTED".equals(body.providerStatus())) {
            throw new PaymentProviderException(HttpStatus.BAD_GATEWAY, "Payment provider returned an invalid response");
        }
        return body;
    }

    private PaymentProviderException translate(RuntimeException exception) {
        if (exception instanceof PaymentProviderException providerException) {
            return providerException;
        }
        if (exception instanceof ResourceAccessException) {
            return new PaymentProviderException(HttpStatus.GATEWAY_TIMEOUT, "Payment provider timed out");
        }
        if (exception instanceof HttpServerErrorException) {
            return new PaymentProviderException(HttpStatus.SERVICE_UNAVAILABLE, "Payment provider is unavailable");
        }
        if (exception instanceof HttpClientErrorException) {
            return new PaymentProviderException(HttpStatus.BAD_GATEWAY, "Payment provider rejected the request");
        }
        return new PaymentProviderException(HttpStatus.SERVICE_UNAVAILABLE, "Payment provider is unavailable");
    }
}

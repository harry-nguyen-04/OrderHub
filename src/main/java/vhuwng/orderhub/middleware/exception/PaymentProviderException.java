package vhuwng.orderhub.middleware.exception;

import org.springframework.http.HttpStatus;

public class PaymentProviderException extends RuntimeException {
    private final HttpStatus status;

    public PaymentProviderException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

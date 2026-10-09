package vhuwng.orderhub.middleware.exception;

public class InvalidPaymentWebhookException extends RuntimeException {
    public InvalidPaymentWebhookException(String message) {
        super(message);
    }
}

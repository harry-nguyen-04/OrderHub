package vhuwng.orderhub.middleware.exception;

public class InvoiceConflictException extends RuntimeException {
    public InvoiceConflictException(String message) {
        super(message);
    }
}

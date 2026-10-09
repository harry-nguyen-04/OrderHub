package vhuwng.orderhub.middleware.exception;

public class InvoiceFileTooLargeException extends RuntimeException {
    public InvoiceFileTooLargeException(String message) {
        super(message);
    }
}

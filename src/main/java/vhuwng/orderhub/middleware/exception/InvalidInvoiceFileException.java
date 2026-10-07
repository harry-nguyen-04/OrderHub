package vhuwng.orderhub.middleware.exception;

public class InvalidInvoiceFileException extends RuntimeException {
    public InvalidInvoiceFileException(String message) {
        super(message);
    }
}

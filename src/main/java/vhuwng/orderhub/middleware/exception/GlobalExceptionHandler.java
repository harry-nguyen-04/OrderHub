package vhuwng.orderhub.middleware.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import jakarta.persistence.EntityNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return new ResponseEntity<>(errors, HttpStatus.UNPROCESSABLE_CONTENT);
    }
    
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleEntityNotFoundException(EntityNotFoundException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put("message", ex.getMessage());
        return new ResponseEntity<>(errors, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put("message", ex.getMessage());
        return new ResponseEntity<>(errors, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateResourceException(DuplicateResourceException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put("message", ex.getMessage());
        return new ResponseEntity<>(errors, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorizedException(UnauthorizedException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put("message", ex.getMessage());
        return new ResponseEntity<>(errors, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<Map<String, String>> handleInsufficientStockException(InsufficientStockException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put("message", ex.getMessage());
        return new ResponseEntity<>(errors, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<Map<String, String>> handleInvalidOrderException(InvalidOrderException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put("message", ex.getMessage());
        return new ResponseEntity<>(errors, HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @ExceptionHandler(PaymentConflictException.class)
    public ResponseEntity<Map<String, String>> handlePaymentConflictException(PaymentConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(InvoiceConflictException.class)
    public ResponseEntity<Map<String, String>> handleInvoiceConflictException(InvoiceConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(InvalidInvoiceFileException.class)
    public ResponseEntity<Map<String, String>> handleInvalidInvoiceFileException(InvalidInvoiceFileException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, String>> handleMissingServletRequestPartException(
            MissingServletRequestPartException ex
    ) {
        return ResponseEntity.badRequest().body(Map.of("message", "Invoice file is required"));
    }

    @ExceptionHandler(UnsupportedInvoiceMediaException.class)
    public ResponseEntity<Map<String, String>> handleUnsupportedInvoiceMediaException(
            UnsupportedInvoiceMediaException ex
    ) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler({InvoiceFileTooLargeException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<Map<String, String>> handleInvoiceFileTooLargeException(Exception ex) {
        String message = ex instanceof InvoiceFileTooLargeException
                ? ex.getMessage()
                : "Invoice file exceeds the 10 MiB limit";
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(Map.of("message", message));
    }

    @ExceptionHandler(InvoiceStorageException.class)
    public ResponseEntity<Map<String, String>> handleInvoiceStorageException(InvoiceStorageException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(InvalidPaymentWebhookException.class)
    public ResponseEntity<Map<String, String>> handleInvalidPaymentWebhookException(InvalidPaymentWebhookException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(PaymentProviderException.class)
    public ResponseEntity<Map<String, String>> handlePaymentProviderException(PaymentProviderException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ResponseEntity<Map<String, String>> handleInvalidIdempotencyKeyException(InvalidIdempotencyKeyException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Map<String, String>> handleIdempotencyConflictException(IdempotencyConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleOptimisticLockingFailureException(
            OptimisticLockingFailureException ex
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "The product was updated by another request. Please retry."));
    }
}

package vhuwng.orderhub.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.multipart.MultipartFile;

import vhuwng.orderhub.dto.response.MeResponseDto;
import vhuwng.orderhub.dto.response.OrderInvoiceResponseDto;
import vhuwng.orderhub.entity.InvoiceEntity;
import vhuwng.orderhub.entity.OrderEntity;
import vhuwng.orderhub.entity.OrderStatus;
import vhuwng.orderhub.entity.UserEntity;
import vhuwng.orderhub.middleware.exception.InvoiceConflictException;
import vhuwng.orderhub.middleware.exception.InvoiceStorageException;
import vhuwng.orderhub.middleware.exception.InvalidInvoiceFileException;
import vhuwng.orderhub.middleware.exception.ResourceNotFoundException;
import vhuwng.orderhub.properties.SeaweedFsProperties;
import vhuwng.orderhub.repository.InvoiceRepository;
import vhuwng.orderhub.repository.OrderRepository;
import vhuwng.orderhub.service.AuthService;
import vhuwng.orderhub.service.InvoiceFileValidator;
import vhuwng.orderhub.service.InvoiceFileValidator.ValidatedInvoiceFile;
import vhuwng.orderhub.service.InvoiceStorage;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {
    @Mock
    private InvoiceFileValidator fileValidator;
    @Mock
    private InvoiceStorage storage;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private AuthService authService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;
    @Mock
    private MultipartFile file;

    private InvoiceServiceImpl invoiceService;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        invoiceService = new InvoiceServiceImpl(
                fileValidator,
                storage,
                invoiceRepository,
                orderRepository,
                authService,
                new SeaweedFsProperties(
                        "http://localhost:8333",
                        "us-east-1",
                        "orderhub",
                        "orderhub-secret",
                        "orderhub",
                        "/orderhub/invoices/"
                ),
                transactionManager
        );
    }

    @Test
    void uploadStoresObjectAndInvoiceMetadataForPaidOwner() throws IOException {
        ValidatedInvoiceFile validated = new ValidatedInvoiceFile("application/pdf", "pdf", 128);
        when(fileValidator.validate(file)).thenReturn(validated);
        when(authService.getCurrentUser()).thenReturn(user(5L));
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order(42L, 5L, OrderStatus.PAID)));
        when(file.getInputStream()).thenReturn(InputStream.nullInputStream());
        when(invoiceRepository.saveAndFlush(any(InvoiceEntity.class))).thenAnswer(invocation -> {
            InvoiceEntity invoice = invocation.getArgument(0);
            invoice.setId(7L);
            return invoice;
        });

        OrderInvoiceResponseDto response = invoiceService.upload(42L, file);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).store(key.capture(), any(), eq(128L), eq("application/pdf"));
        assertTrue(key.getValue().startsWith("orderhub/invoices/orders/42/invoices/"));
        assertTrue(key.getValue().endsWith(".pdf"));
        assertEquals(7L, response.id());
        assertEquals(42L, response.orderId());
        assertEquals("application/pdf", response.mimeType());
        assertEquals(128L, response.sizeBytes());
        verify(storage, never()).delete(any());
    }

    @Test
    void uploadRejectsMissingOrderAndAnotherUsersOrder() {
        when(fileValidator.validate(file)).thenReturn(new ValidatedInvoiceFile("image/png", "png", 8));
        when(authService.getCurrentUser()).thenReturn(user(5L));
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> invoiceService.upload(42L, file));

        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order(42L, 9L, OrderStatus.PAID)));
        assertThrows(ResourceNotFoundException.class, () -> invoiceService.upload(42L, file));
        verify(storage, never()).store(any(), any(), any(Long.class), any());
    }

    @Test
    void uploadRejectsOrderThatIsNotPaid() {
        when(fileValidator.validate(file)).thenReturn(new ValidatedInvoiceFile("image/jpeg", "jpg", 8));
        when(authService.getCurrentUser()).thenReturn(user(5L));
        when(orderRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(order(42L, 5L, OrderStatus.PENDING)));

        assertThrows(InvoiceConflictException.class, () -> invoiceService.upload(42L, file));
        verify(storage, never()).store(any(), any(), any(Long.class), any());
    }

    @Test
    void uploadDoesNotSaveMetadataWhenStorageFails() throws IOException {
        when(fileValidator.validate(file)).thenReturn(new ValidatedInvoiceFile("application/pdf", "pdf", 8));
        when(authService.getCurrentUser()).thenReturn(user(5L));
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order(42L, 5L, OrderStatus.PAID)));
        when(file.getInputStream()).thenReturn(InputStream.nullInputStream());
        org.mockito.Mockito.doThrow(new InvoiceStorageException("down", new IOException("down")))
                .when(storage).store(any(), any(), eq(8L), eq("application/pdf"));

        assertThrows(InvoiceStorageException.class, () -> invoiceService.upload(42L, file));
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test
    void uploadDeletesObjectWhenMetadataWriteFails() throws IOException {
        when(fileValidator.validate(file)).thenReturn(new ValidatedInvoiceFile("application/pdf", "pdf", 8));
        when(authService.getCurrentUser()).thenReturn(user(5L));
        when(orderRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(order(42L, 5L, OrderStatus.PAID)))
                .thenReturn(Optional.of(order(42L, 5L, OrderStatus.PENDING)));
        when(file.getInputStream()).thenReturn(InputStream.nullInputStream());

        assertThrows(InvoiceConflictException.class, () -> invoiceService.upload(42L, file));
        verify(storage).delete(any());
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test
    void uploadKeepsMetadataErrorWhenCompensationDeleteFails() throws IOException {
        when(fileValidator.validate(file)).thenReturn(new ValidatedInvoiceFile("application/pdf", "pdf", 8));
        when(authService.getCurrentUser()).thenReturn(user(5L));
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order(42L, 5L, OrderStatus.PAID)));
        when(file.getInputStream()).thenReturn(InputStream.nullInputStream());
        when(invoiceRepository.saveAndFlush(any())).thenThrow(new IllegalStateException("db down"));
        org.mockito.Mockito.doThrow(new InvoiceStorageException("cleanup failed", new IOException("cleanup")))
                .when(storage).delete(any());

        assertThrows(IllegalStateException.class, () -> invoiceService.upload(42L, file));
    }

    @Test
    void uploadRejectsUnreadableContentAfterValidation() throws IOException {
        when(fileValidator.validate(file)).thenReturn(new ValidatedInvoiceFile("application/pdf", "pdf", 8));
        when(authService.getCurrentUser()).thenReturn(user(5L));
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order(42L, 5L, OrderStatus.PAID)));
        when(file.getInputStream()).thenThrow(new IOException("closed"));

        assertThrows(InvalidInvoiceFileException.class, () -> invoiceService.upload(42L, file));
        verify(storage, never()).store(any(), any(), any(Long.class), any());
    }

    private static MeResponseDto user(Long id) {
        return new MeResponseDto(id, "alice", "Alice", "USER");
    }

    private static OrderEntity order(Long id, Long userId, OrderStatus status) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        OrderEntity order = new OrderEntity();
        order.setId(id);
        order.setUser(user);
        order.setStatus(status);
        return order;
    }
}

package vhuwng.orderhub.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import vhuwng.orderhub.dto.response.OrderInvoiceResponseDto;
import vhuwng.orderhub.entity.InvoiceEntity;
import vhuwng.orderhub.entity.OrderEntity;
import vhuwng.orderhub.entity.OrderStatus;
import vhuwng.orderhub.middleware.exception.InvalidInvoiceFileException;
import vhuwng.orderhub.middleware.exception.InvoiceConflictException;
import vhuwng.orderhub.middleware.exception.ResourceNotFoundException;
import vhuwng.orderhub.properties.SeaweedFsProperties;
import vhuwng.orderhub.repository.InvoiceRepository;
import vhuwng.orderhub.repository.OrderRepository;
import vhuwng.orderhub.service.AuthService;
import vhuwng.orderhub.service.InvoiceFileValidator;
import vhuwng.orderhub.service.InvoiceFileValidator.ValidatedInvoiceFile;
import vhuwng.orderhub.service.InvoiceService;
import vhuwng.orderhub.service.InvoiceStorage;

@Service
public class InvoiceServiceImpl implements InvoiceService {
    private static final Logger log = LoggerFactory.getLogger(InvoiceServiceImpl.class);

    private final InvoiceFileValidator fileValidator;
    private final InvoiceStorage storage;
    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final AuthService authService;
    private final SeaweedFsProperties properties;
    private final TransactionTemplate transactionTemplate;

    public InvoiceServiceImpl(
            InvoiceFileValidator fileValidator,
            InvoiceStorage storage,
            InvoiceRepository invoiceRepository,
            OrderRepository orderRepository,
            AuthService authService,
            SeaweedFsProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this.fileValidator = fileValidator;
        this.storage = storage;
        this.invoiceRepository = invoiceRepository;
        this.orderRepository = orderRepository;
        this.authService = authService;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public OrderInvoiceResponseDto upload(Long orderId, MultipartFile file) {
        ValidatedInvoiceFile validated = fileValidator.validate(file);
        Long currentUserId = authService.getCurrentUser().id();
        transactionTemplate.executeWithoutResult(status -> requirePaidOrder(orderId, currentUserId));

        String storageKey = storageKey(orderId, validated.extension());
        try (InputStream content = file.getInputStream()) {
            storage.store(storageKey, content, validated.sizeBytes(), validated.mimeType());
        } catch (IOException ex) {
            throw new InvalidInvoiceFileException("Unable to read the invoice file");
        }

        try {
            InvoiceEntity saved = transactionTemplate.execute(
                    status -> saveInvoice(orderId, currentUserId, storageKey, validated));
            if (saved == null) {
                throw new IllegalStateException("Unable to save invoice metadata");
            }
            return OrderInvoiceResponseDto.fromEntity(saved);
        } catch (RuntimeException ex) {
            deleteUploadedObject(storageKey);
            throw ex;
        }
    }

    private OrderEntity requirePaidOrder(Long orderId, Long currentUserId) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));
        if (order.getUser() == null || !currentUserId.equals(order.getUser().getId())) {
            throw new ResourceNotFoundException("Order with id " + orderId + " not found");
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new InvoiceConflictException("Order must be paid before uploading an invoice");
        }
        return order;
    }

    private InvoiceEntity saveInvoice(
            Long orderId,
            Long currentUserId,
            String storageKey,
            ValidatedInvoiceFile validated
    ) {
        OrderEntity order = requirePaidOrder(orderId, currentUserId);
        InvoiceEntity invoice = new InvoiceEntity();
        invoice.setOrder(order);
        invoice.setStorageKey(storageKey);
        invoice.setMimeType(validated.mimeType());
        invoice.setSizeBytes(validated.sizeBytes());
        invoice.setUploadedAt(Instant.now());
        return invoiceRepository.saveAndFlush(invoice);
    }

    private String storageKey(Long orderId, String extension) {
        return normalizeBasePath(properties.basePath())
                + "/orders/" + orderId + "/invoices/" + UUID.randomUUID() + "." + extension;
    }

    private static String normalizeBasePath(String basePath) {
        String path = basePath == null ? "" : basePath.trim();
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/") && !path.isEmpty()) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return "orderhub/invoices";
        }
        return path;
    }

    private void deleteUploadedObject(String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (RuntimeException ex) {
            log.warn("Failed to delete invoice object {} after metadata write failed", storageKey, ex);
        }
    }
}

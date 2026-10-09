package vhuwng.orderhub.service.impl;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;
import vhuwng.orderhub.dto.request.CreateOrderItemRequestDto;
import vhuwng.orderhub.dto.request.CreateOrderRequestDto;
import vhuwng.orderhub.dto.response.CreateOrderResponseDto;
import vhuwng.orderhub.dto.response.IdempotencyResultDto;
import vhuwng.orderhub.entity.IdempotencyKeyEntity;
import vhuwng.orderhub.entity.IdempotencyOperation;
import vhuwng.orderhub.entity.IdempotencyStatus;
import vhuwng.orderhub.middleware.exception.IdempotencyConflictException;
import vhuwng.orderhub.middleware.exception.InvalidIdempotencyKeyException;
import vhuwng.orderhub.repository.IdempotencyRepository;
import vhuwng.orderhub.repository.UserRepository;
import vhuwng.orderhub.security.TokenHasher;
import vhuwng.orderhub.service.AuthService;
import vhuwng.orderhub.service.IdempotencyService;
import vhuwng.orderhub.service.OrderService;

@Service
public class IdempotencyServiceImpl implements IdempotencyService {
    private static final int CREATED = 201;
    private static final int MAX_KEY_LENGTH = 128;
    private static final int MAX_ATTEMPTS = 3;

    private final IdempotencyRepository idempotencyRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public IdempotencyServiceImpl(
            IdempotencyRepository idempotencyRepository,
            UserRepository userRepository,
            AuthService authService,
            OrderService orderService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.idempotencyRepository = idempotencyRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.orderService = orderService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /*
     * Case study - Why TransactionTemplate?
     *
     * Request 1: key="abc"
     *   BEGIN TX
     *   -> INSERT key abc
     *   -> create order + decrease stock
     *   -> save response
     *   -> COMMIT
     *
     * Request 2 retries with the same key="abc":
     *   BEGIN TX
     *   -> INSERT key abc
     *   -> UNIQUE constraint violation
     *   -> throw ReservationConflictException
     *   -> ROLLBACK TX
     *   -> exit transactionTemplate.execute(...)
     *   -> catch below runs
     *   -> read the existing key abc
     *   -> replay the stored response
     *
     * Important:
     * We need the failed transaction to ROLLBACK BEFORE reading
     * the existing idempotency record.
     *
     * TransactionTemplate gives us this explicit boundary:
     *
     *   [ INSERT key -> create order -> save response ]
     *   [           COMMIT / ROLLBACK                ]
     *                         |
     *                         v
     *                  catch + read old key
     *
     * @Transactional can achieve the same behavior, but the
     * transactional part should be moved to another Spring bean/method
     * so its transaction ends before this catch block executes.
     */
    @Override
    public IdempotencyResultDto<CreateOrderResponseDto> createOrder(String key, CreateOrderRequestDto request) {
        validateKey(key);
        Long userId = authService.getCurrentUser().id();
        String requestHash = hashOrderRequest(request);

        try {
            return executeWithOptimisticRetry(() -> transactionTemplate.execute(status -> {
                IdempotencyKeyEntity record = new IdempotencyKeyEntity();
                record.setKey(key);
                record.setUser(userRepository.getReferenceById(userId));
                record.setOperation(IdempotencyOperation.CREATE_ORDER);
                record.setRequestHash(requestHash);
                record.setStatus(IdempotencyStatus.IN_PROGRESS);
                try {
                    // The unique constraint waits for another request with this key to commit or roll back.
                    record = idempotencyRepository.saveAndFlush(record);
                } catch (DataIntegrityViolationException ex) {
                    throw new ReservationConflictException(ex);
                }

                CreateOrderResponseDto response = orderService.createOrder(request);
                record.setResponseStatus(CREATED);
                record.setResponseBody(objectMapper.writeValueAsString(response));
                record.setStatus(IdempotencyStatus.COMPLETED);
                idempotencyRepository.saveAndFlush(record);
                return new IdempotencyResultDto<>(CREATED, response, false);
            }));
        } catch (ReservationConflictException ex) {
            // Read in a new transaction after the failed insert has rolled back.
            IdempotencyKeyEntity existing = idempotencyRepository
                    .findByUserIdAndOperationAndKey(userId, IdempotencyOperation.CREATE_ORDER, key)
                    .orElseThrow(() -> ex.original);
            if (!Objects.equals(existing.getRequestHash(), requestHash)) {
                throw new IdempotencyConflictException("Idempotency-Key was already used with a different request");
            }
            if (existing.getStatus() != IdempotencyStatus.COMPLETED
                    || existing.getResponseStatus() == null
                    || existing.getResponseBody() == null) {
                throw new IdempotencyConflictException("Idempotency-Key is still being processed");
            }
            CreateOrderResponseDto response = objectMapper.readValue(
                    existing.getResponseBody(), CreateOrderResponseDto.class);
            return new IdempotencyResultDto<>(existing.getResponseStatus(), response, true);
        }
    }

    private <T> T executeWithOptimisticRetry(Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (OptimisticLockingFailureException ex) {
                // execute() has rolled back before we retry in a new transaction.
                if (attempt >= MAX_ATTEMPTS) {
                    throw ex;
                }
            }
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH || !key.equals(key.strip())) {
            throw new InvalidIdempotencyKeyException("Idempotency-Key must be 1 to 128 characters without surrounding whitespace");
        }
    }

    private String hashOrderRequest(CreateOrderRequestDto request) {
        StringBuilder canonical = new StringBuilder();
        request.items().stream()
                .sorted(Comparator.comparing(CreateOrderItemRequestDto::productId)
                        .thenComparing(CreateOrderItemRequestDto::quantity))
                .forEach(item -> canonical.append(item.productId())
                        .append(':').append(item.quantity()).append('\n'));
        return TokenHasher.sha256(canonical.toString());
    }

    private static final class ReservationConflictException extends RuntimeException {
        private final DataIntegrityViolationException original;

        private ReservationConflictException(DataIntegrityViolationException original) {
            super(original);
            this.original = original;
        }
    }
}

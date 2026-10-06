package vhuwng.orderhub.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import vhuwng.orderhub.dto.request.CreateOrderItemRequestDto;
import vhuwng.orderhub.dto.request.CreateOrderRequestDto;
import vhuwng.orderhub.dto.response.CreateOrderResponseDto;
import vhuwng.orderhub.dto.response.IdempotencyResultDto;
import vhuwng.orderhub.entity.ProductEntity;
import vhuwng.orderhub.entity.Role;
import vhuwng.orderhub.entity.UserEntity;
import vhuwng.orderhub.middleware.exception.IdempotencyConflictException;
import vhuwng.orderhub.middleware.exception.InsufficientStockException;
import vhuwng.orderhub.repository.OrderRepository;
import vhuwng.orderhub.repository.ProductRepository;
import vhuwng.orderhub.repository.UserRepository;
import vhuwng.orderhub.service.IdempotencyService;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "IDEMPOTENCY_INTEGRATION", matches = "true")
class IdempotencyServiceIntegrationTest {
    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void concurrentRetriesCreateOneOrderAndDecrementStockOnce() throws Exception {
        UserEntity user = user();
        ProductEntity product = product(2);
        CreateOrderRequestDto request = request(product.getId(), 1);
        long ordersBefore = orderRepository.count();
        String key = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<IdempotencyResultDto<CreateOrderResponseDto>> first = pool.submit(() -> {
                start.await();
                return createAs(user.getUsername(), key, request);
            });
            Future<IdempotencyResultDto<CreateOrderResponseDto>> second = pool.submit(() -> {
                start.await();
                return createAs(user.getUsername(), key, request);
            });
            start.countDown();

            IdempotencyResultDto<CreateOrderResponseDto> a = first.get(30, TimeUnit.SECONDS);
            IdempotencyResultDto<CreateOrderResponseDto> b = second.get(30, TimeUnit.SECONDS);
            assertEquals(a.body(), b.body());
            assertEquals(201, a.responseStatus());
            assertEquals(201, b.responseStatus());
            assertTrue(a.replayed() != b.replayed());
        }

        assertEquals(ordersBefore + 1, orderRepository.count());
        assertEquals(1, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void sameKeyWithDifferentRequestReturnsConflict() {
        UserEntity user = user();
        ProductEntity product = product(3);
        String key = UUID.randomUUID().toString();
        authenticate(user.getUsername());
        try {
            IdempotencyResultDto<CreateOrderResponseDto> first =
                    idempotencyService.createOrder(key, request(product.getId(), 1));
            assertFalse(first.replayed());
            assertThrows(IdempotencyConflictException.class,
                    () -> idempotencyService.createOrder(key, request(product.getId(), 2)));
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertEquals(2, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void sameKeyBelongingToDifferentUsersCreatesSeparateOrders() {
        UserEntity firstUser = user();
        UserEntity secondUser = user();
        ProductEntity product = product(2);
        CreateOrderRequestDto request = request(product.getId(), 1);
        String key = UUID.randomUUID().toString();
        long ordersBefore = orderRepository.count();

        IdempotencyResultDto<CreateOrderResponseDto> first = createAs(firstUser.getUsername(), key, request);
        IdempotencyResultDto<CreateOrderResponseDto> second = createAs(secondUser.getUsername(), key, request);

        assertFalse(first.replayed());
        assertFalse(second.replayed());
        assertEquals(firstUser.getId(), first.body().userId());
        assertEquals(secondUser.getId(), second.body().userId());
        assertEquals(ordersBefore + 2, orderRepository.count());
        assertEquals(0, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void failedOrderDoesNotReserveKey() {
        UserEntity user = user();
        ProductEntity product = product(0);
        String key = UUID.randomUUID().toString();
        authenticate(user.getUsername());
        try {
            assertThrows(InsufficientStockException.class,
                    () -> idempotencyService.createOrder(key, request(product.getId(), 1)));
            product.setStock(1);
            productRepository.save(product);
            IdempotencyResultDto<CreateOrderResponseDto> retry =
                    idempotencyService.createOrder(key, request(product.getId(), 1));
            assertFalse(retry.replayed());
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertEquals(0, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    private IdempotencyResultDto<CreateOrderResponseDto> createAs(
            String username, String key, CreateOrderRequestDto request) {
        authenticate(username);
        try {
            return idempotencyService.createOrder(key, request);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username, "", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setUsername("idempotency-" + UUID.randomUUID());
        user.setHashPassword("unused");
        user.setRole(Role.USER);
        return userRepository.save(user);
    }

    private ProductEntity product(int stock) {
        ProductEntity product = new ProductEntity();
        product.setSku("idem-" + UUID.randomUUID());
        product.setName("Idempotency test product");
        product.setUnitPrice(new BigDecimal("10.00"));
        product.setCurrency("USD");
        product.setStock(stock);
        product.setIsActive(true);
        return productRepository.save(product);
    }

    private CreateOrderRequestDto request(Long productId, int quantity) {
        return new CreateOrderRequestDto(List.of(new CreateOrderItemRequestDto(productId, quantity)));
    }
}

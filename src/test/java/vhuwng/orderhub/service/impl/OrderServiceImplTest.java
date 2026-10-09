package vhuwng.orderhub.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import vhuwng.orderhub.dto.request.CreateOrderItemRequestDto;
import vhuwng.orderhub.dto.request.CreateOrderRequestDto;
import vhuwng.orderhub.dto.response.CreateOrderResponseDto;
import vhuwng.orderhub.dto.response.OrderResponseDto;
import vhuwng.orderhub.entity.OrderEntity;
import vhuwng.orderhub.entity.OrderItemEntity;
import vhuwng.orderhub.entity.OrderStatus;
import vhuwng.orderhub.entity.ProductEntity;
import vhuwng.orderhub.entity.UserEntity;
import vhuwng.orderhub.middleware.exception.InsufficientStockException;
import vhuwng.orderhub.middleware.exception.InvalidOrderException;
import vhuwng.orderhub.middleware.exception.ResourceNotFoundException;
import vhuwng.orderhub.repository.OrderRepository;
import vhuwng.orderhub.repository.ProductRepository;
import vhuwng.orderhub.repository.UserRepository;
import vhuwng.orderhub.util.RedisCacheUtil;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RedisCacheUtil cacheUtil;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderRepository, productRepository, userRepository, cacheUtil);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createOrderDecrementsStockAndSavesTotal() {
        authenticate("alice", "USER");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(5L, "alice")));
        ProductEntity mug = product(1L, "Mug", "12.50", "USD", 10, true);
        ProductEntity pen = product(2L, "Pen", "3.00", "USD", 4, true);
        when(productRepository.findAllByIdInOrderById(any())).thenReturn(List.of(mug, pen));
        when(orderRepository.existsByCode(any())).thenReturn(false);
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity saved = invocation.getArgument(0);
            saved.setId(20L);
            return saved;
        });

        CreateOrderRequestDto request = new CreateOrderRequestDto(List.of(
                new CreateOrderItemRequestDto(2L, 1),
                new CreateOrderItemRequestDto(1L, 2)
        ));

        CreateOrderResponseDto response = orderService.createOrder(request);

        assertEquals(new BigDecimal("28.00"), response.totalAmount());
        assertEquals(OrderStatus.PENDING.name(), response.status());
        assertEquals(5L, response.userId());
        assertEquals(2, response.items().size());
        assertEquals(8, mug.getStock());
        assertEquals(3, pen.getStock());

        ArgumentCaptor<OrderEntity> orderCaptor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(new BigDecimal("28.00"), orderCaptor.getValue().getTotalAmount());
        assertEquals(OrderStatus.PENDING, orderCaptor.getValue().getStatus());
        assertEquals(2, orderCaptor.getValue().getItems().size());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> idsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(productRepository).findAllByIdInOrderById(idsCaptor.capture());
        assertEquals(List.of(1L, 2L), List.copyOf(idsCaptor.getValue()));
    }

    @Test
    void createOrderInsufficientStockDoesNotSave() {
        authenticate("alice", "USER");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(5L, "alice")));
        ProductEntity mug = product(1L, "Mug", "12.50", "USD", 1, true);
        when(productRepository.findAllByIdInOrderById(any())).thenReturn(List.of(mug));

        CreateOrderRequestDto request = new CreateOrderRequestDto(List.of(
                new CreateOrderItemRequestDto(1L, 2)
        ));

        assertThrows(InsufficientStockException.class, () -> orderService.createOrder(request));
        assertEquals(1, mug.getStock());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderMissingProductThrows() {
        authenticate("alice", "USER");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(5L, "alice")));
        when(productRepository.findAllByIdInOrderById(any())).thenReturn(List.of(
                product(1L, "Mug", "12.50", "USD", 10, true)
        ));

        CreateOrderRequestDto request = new CreateOrderRequestDto(List.of(
                new CreateOrderItemRequestDto(1L, 1),
                new CreateOrderItemRequestDto(9L, 1)
        ));

        assertThrows(ResourceNotFoundException.class, () -> orderService.createOrder(request));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderInactiveProductThrows() {
        authenticate("alice", "USER");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(5L, "alice")));
        ProductEntity mug = product(1L, "Mug", "12.50", "USD", 10, false);
        when(productRepository.findAllByIdInOrderById(any())).thenReturn(List.of(mug));

        CreateOrderRequestDto request = new CreateOrderRequestDto(List.of(
                new CreateOrderItemRequestDto(1L, 1)
        ));

        assertThrows(InvalidOrderException.class, () -> orderService.createOrder(request));
        assertEquals(10, mug.getStock());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderDuplicateProductThrows() {
        authenticate("alice", "USER");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(5L, "alice")));

        CreateOrderRequestDto request = new CreateOrderRequestDto(List.of(
                new CreateOrderItemRequestDto(1L, 1),
                new CreateOrderItemRequestDto(1L, 2)
        ));

        assertThrows(InvalidOrderException.class, () -> orderService.createOrder(request));
        verify(productRepository, never()).findAllByIdInOrderById(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void getOrdersForUserUsesOwnOrders() {
        authenticate("alice", "USER");
        UserEntity alice = user(5L, "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        OrderEntity summary = ownedOrder(8L, alice);
        OrderEntity detailed = ownedOrder(8L, alice);
        detailed.setItems(List.of(item(detailed, product(1L, "Mug", "12.50", "USD", 10, true), 2)));
        when(orderRepository.findByUserId(eq(5L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(summary)));
        when(orderRepository.findWithDetailsByIdIn(any())).thenReturn(List.of(detailed));

        Page<OrderResponseDto> page = orderService.getOrders(0, 10);

        assertEquals(1, page.getContent().size());
        assertEquals(8L, page.getContent().get(0).id());
        assertEquals(5L, page.getContent().get(0).userId());
        assertEquals(1, page.getContent().get(0).items().size());
        verify(orderRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void getOrderByIdForAnotherUserThrows() {
        authenticate("alice", "USER");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(5L, "alice")));
        when(orderRepository.findWithDetailsById(3L)).thenReturn(Optional.of(ownedOrder(3L, user(9L, "bob"))));

        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderById(3L));
    }

    @Test
    void getOrderByIdForOwnerReturnsOrder() {
        authenticate("alice", "USER");
        UserEntity alice = user(5L, "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        OrderEntity order = ownedOrder(3L, alice);
        order.setItems(List.of(item(order, product(1L, "Mug", "12.50", "USD", 10, true), 1)));
        when(orderRepository.findWithDetailsById(3L)).thenReturn(Optional.of(order));

        OrderResponseDto response = orderService.getOrderById(3L);

        assertEquals(3L, response.id());
        assertEquals("alice", response.username());
    }

    @Test
    void getOrderByIdForAdminReturnsAnotherUsersOrder() {
        authenticate("admin", "ADMIN");
        OrderEntity order = ownedOrder(3L, user(9L, "bob"));
        order.setItems(List.of(item(order, product(1L, "Mug", "12.50", "USD", 10, true), 1)));
        when(orderRepository.findWithDetailsById(3L)).thenReturn(Optional.of(order));

        OrderResponseDto response = orderService.getOrderById(3L);

        assertEquals(9L, response.userId());
        assertEquals("bob", response.username());
        verify(userRepository, never()).findByUsername(any());
    }

    private void authenticate(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        AuthorityUtils.createAuthorityList("ROLE_" + role)
                )
        );
    }

    private UserEntity user(Long id, String username) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private ProductEntity product(Long id, String name, String price, String currency, int stock, boolean active) {
        ProductEntity product = new ProductEntity();
        product.setId(id);
        product.setName(name);
        product.setUnitPrice(new BigDecimal(price));
        product.setCurrency(currency);
        product.setStock(stock);
        product.setIsActive(active);
        return product;
    }

    private OrderEntity ownedOrder(Long id, UserEntity owner) {
        OrderEntity order = new OrderEntity();
        order.setId(id);
        order.setUser(owner);
        order.setCode("ORD-TEST");
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("12.50"));
        return order;
    }

    private OrderItemEntity item(OrderEntity order, ProductEntity product, int quantity) {
        OrderItemEntity item = new OrderItemEntity();
        item.setId(1L);
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setUnitPrice(product.getUnitPrice());
        return item;
    }
}

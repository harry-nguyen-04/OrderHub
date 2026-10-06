package vhuwng.orderhub.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import vhuwng.orderhub.middleware.exception.UnauthorizedException;
import vhuwng.orderhub.repository.OrderRepository;
import vhuwng.orderhub.repository.ProductRepository;
import vhuwng.orderhub.repository.UserRepository;
import vhuwng.orderhub.service.OrderService;

@Service
public class OrderServiceImpl implements OrderService {
    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";
    private static final int CODE_ATTEMPTS = 5;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            UserRepository userRepository
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public CreateOrderResponseDto createOrder(CreateOrderRequestDto request) {
        UserEntity user = currentUser();
        Map<Long, Integer> quantityByProductId = quantitiesByProductId(request.items());
        List<Long> productIds = quantityByProductId.keySet().stream().sorted().toList();
        List<ProductEntity> products = productRepository.findAllByIdInOrderById(productIds);
        ensureProductsFound(productIds, products);
        ensureOrderable(products, quantityByProductId);

        OrderEntity order = new OrderEntity();
        order.setUser(user);
        order.setCode(generateCode());
        order.setStatus(OrderStatus.PENDING);
        order.setItems(buildItems(order, products, quantityByProductId));
        order.setTotalAmount(totalAmount(order.getItems()));
        order = orderRepository.save(order);
        return CreateOrderResponseDto.fromEntity(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponseDto> getOrders(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderEntity> orders = isAdmin()
                ? orderRepository.findAll(pageable)
                : orderRepository.findByUserId(currentUser().getId(), pageable);
        List<Long> ids = orders.getContent().stream().map(OrderEntity::getId).toList();
        Map<Long, OrderEntity> detailedById = ids.isEmpty()
                ? Map.of()
                : orderRepository.findWithDetailsByIdIn(ids).stream()
                        .collect(Collectors.toMap(OrderEntity::getId, Function.identity()));
        return orders.map(order -> OrderResponseDto.fromEntity(detailedById.getOrDefault(order.getId(), order)));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDto getOrderById(Long id) {
        OrderEntity order = orderRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + id + " not found"));
        if (!isAdmin() && !order.getUser().getId().equals(currentUser().getId())) {
            throw new ResourceNotFoundException("Order with id " + id + " not found");
        }
        return OrderResponseDto.fromEntity(order);
    }

    private Map<Long, Integer> quantitiesByProductId(List<CreateOrderItemRequestDto> items) {
        Map<Long, Integer> quantityByProductId = new LinkedHashMap<>();
        for (CreateOrderItemRequestDto item : items) {
            if (quantityByProductId.containsKey(item.productId())) {
                throw new InvalidOrderException("Duplicate product id " + item.productId());
            }
            quantityByProductId.put(item.productId(), item.quantity());
        }
        return quantityByProductId;
    }

    private void ensureProductsFound(List<Long> productIds, List<ProductEntity> products) {
        Set<Long> foundIds = products.stream().map(ProductEntity::getId).collect(Collectors.toSet());
        for (Long productId : productIds) {
            if (!foundIds.contains(productId)) {
                throw new ResourceNotFoundException("Product with id " + productId + " not found");
            }
        }
    }

    private void ensureOrderable(List<ProductEntity> products, Map<Long, Integer> quantityByProductId) {
        String currency = products.get(0).getCurrency();
        for (ProductEntity product : products) {
            if (!Boolean.TRUE.equals(product.getIsActive())) {
                throw new InvalidOrderException("Product with id " + product.getId() + " is not active");
            }
            if (currency == null || !currency.equals(product.getCurrency())) {
                throw new InvalidOrderException("Order items must use the same currency");
            }
            int quantity = quantityByProductId.get(product.getId());
            if (product.getStock() == null || product.getStock() < quantity) {
                throw new InsufficientStockException("Insufficient stock for product " + product.getId());
            }
        }
    }

    private List<OrderItemEntity> buildItems(
            OrderEntity order,
            List<ProductEntity> products,
            Map<Long, Integer> quantityByProductId
    ) {
        List<OrderItemEntity> items = new ArrayList<>();
        for (ProductEntity product : products) {
            int quantity = quantityByProductId.get(product.getId());
            product.setStock(product.getStock() - quantity);
            OrderItemEntity item = new OrderItemEntity();
            item.setOrder(order);
            item.setProduct(product);
            item.setQuantity(quantity);
            item.setUnitPrice(product.getUnitPrice());
            items.add(item);
        }
        return items;
    }

    private BigDecimal totalAmount(List<OrderItemEntity> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemEntity item : items) {
            total = total.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        return total;
    }

    private String generateCode() {
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            String code = "ORD-" + UUID.randomUUID().toString().replace("-", "");
            if (!orderRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new InvalidOrderException("Unable to generate a unique order code");
    }

    private UserEntity currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new UnauthorizedException("Authentication is required"));
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
    }
}

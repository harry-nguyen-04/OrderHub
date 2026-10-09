package vhuwng.orderhub.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import vhuwng.orderhub.dto.request.CreateProductRequestDto;
import vhuwng.orderhub.dto.response.ProductResponseDto;
import vhuwng.orderhub.entity.ProductEntity;
import vhuwng.orderhub.middleware.exception.DuplicateResourceException;
import vhuwng.orderhub.middleware.exception.ResourceNotFoundException;
import vhuwng.orderhub.repository.ProductRepository;
import vhuwng.orderhub.util.RedisCacheUtil;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {
    @Mock
    private ProductRepository productRepository;
    @Mock
    private RedisCacheUtil cacheUtil;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(productRepository, cacheUtil);
    }

    @Test
    void getProductByIdReturnsProduct() {
        ProductEntity product = new ProductEntity();
        product.setId(4L);
        product.setSku("SKU-1");
        product.setName("Mug");
        product.setCategory("Home");
        product.setUnitPrice(new BigDecimal("12.50"));
        product.setCurrency("USD");
        product.setStock(3);
        product.setIsActive(true);
        when(productRepository.findById(4L)).thenReturn(Optional.of(product));

        ProductResponseDto response = productService.getProductById(4L);

        assertEquals(4L, response.id());
        assertEquals("SKU-1", response.sku());
        assertEquals("Mug", response.name());
    }

    @Test
    void getProductByIdMissingThrows() {
        when(productRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.getProductById(9L));
    }

    @Test
    void createProductDuplicateSkuThrows() {
        when(productRepository.existsBySku("SKU-1")).thenReturn(true);
        CreateProductRequestDto request = new CreateProductRequestDto(
                "SKU-1", "Mug", "Home", new BigDecimal("12.50"), "USD", 3, true);

        assertThrows(DuplicateResourceException.class, () -> productService.createProduct(request));
        verify(productRepository, never()).save(any());
    }

    @Test
    void deleteProductMissingThrows() {
        when(productRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.deleteProduct(9L));
        verify(productRepository, never()).save(any());
    }
}

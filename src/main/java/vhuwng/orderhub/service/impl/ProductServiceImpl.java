package vhuwng.orderhub.service.impl;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import vhuwng.orderhub.dto.filter.ProductFilterDto;
import vhuwng.orderhub.dto.request.CreateProductRequestDto;
import vhuwng.orderhub.dto.request.UpdateProductResquestDto;
import vhuwng.orderhub.dto.response.CreateProductResponseDto;
import vhuwng.orderhub.dto.response.ProductResponseDto;
import vhuwng.orderhub.dto.response.UpdateProductResponseDto;
import vhuwng.orderhub.entity.ProductEntity;
import vhuwng.orderhub.middleware.exception.DuplicateResourceException;
import vhuwng.orderhub.middleware.exception.ResourceNotFoundException;
import vhuwng.orderhub.repository.ProductRepository;
import vhuwng.orderhub.repository.specification.ProductSpecifications;
import vhuwng.orderhub.service.ProductService;

@Service
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;

    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public Page<ProductResponseDto> getAllProducts(ProductFilterDto filter) {
        Specification<ProductEntity> spec =
            ProductSpecifications.filter(filter);
        Page<ProductEntity> products = productRepository.findAll(spec, PageRequest.of(filter.page(), filter.size()));
        return products.map(ProductResponseDto::fromEntity);
    }

    @Override
    public ProductResponseDto getProductById(Long id) {
        ProductEntity product = productRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product with id " + id + " not found"));
        return ProductResponseDto.fromEntity(product);
    }
    
    @Override
    public CreateProductResponseDto createProduct(CreateProductRequestDto request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateResourceException("Product", "sku", request.sku());
        }
        ProductEntity product = new ProductEntity();
        product.setSku(request.sku());
        product.setName(request.name());
        product.setCategory(request.category());
        product.setUnitPrice(request.unitPrice());
        product.setCurrency(request.currency());
        product.setStock(request.stock());
        product.setIsActive(request.isActive());
        product.setCreatedAt(Instant.now());
        product.setUpdatedAt(Instant.now());
        product = productRepository.save(product);
        return CreateProductResponseDto.fromEntity(product);
    }

    @Override
    public UpdateProductResponseDto updateProduct(Long id, UpdateProductResquestDto request) {
        ProductEntity product = productRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product with id " + id + " not found"));
        product.setSku(request.sku());
        product.setName(request.name());
        product.setCategory(request.category());
        product.setUnitPrice(request.unitPrice());
        product.setCurrency(request.currency());
        product.setStock(request.stock());
        product.setIsActive(request.isActive());
        product.setUpdatedAt(Instant.now());
        product = productRepository.save(product);
        return UpdateProductResponseDto.fromEntity(product);
    }
    
    @Override
    public void deleteProduct(Long id) {
        ProductEntity product = productRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product with id " + id + " not found"));
        product.setIsActive(false);
        product.setUpdatedAt(Instant.now());
        productRepository.save(product);
    }
    
    
}

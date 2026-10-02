package vhuwng.orderhub.service;

import org.springframework.data.domain.Page;

import vhuwng.orderhub.dto.filter.ProductFilterDto;
import vhuwng.orderhub.dto.request.CreateProductRequestDto;
import vhuwng.orderhub.dto.request.UpdateProductResquestDto;
import vhuwng.orderhub.dto.response.CreateProductResponseDto;
import vhuwng.orderhub.dto.response.ProductResponseDto;
import vhuwng.orderhub.dto.response.UpdateProductResponseDto;

public interface ProductService {
    Page<ProductResponseDto> getAllProducts(ProductFilterDto filter);
    ProductResponseDto getProductById(Long id);
    CreateProductResponseDto createProduct(CreateProductRequestDto request);
    UpdateProductResponseDto updateProduct(Long id, UpdateProductResquestDto request);
    void deleteProduct(Long id);
}

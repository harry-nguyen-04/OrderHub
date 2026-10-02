package vhuwng.orderhub.dto.filter;

import java.math.BigDecimal;

public record ProductFilterDto(
    String sku,
    String name,
    String category,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    String currency,
    Integer page,
    Integer size,
    String sortBy,
    String sortOrder
) {
    
}

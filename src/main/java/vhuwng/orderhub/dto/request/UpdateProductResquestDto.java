package vhuwng.orderhub.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProductResquestDto(
        @NotBlank(message = "SKU is required")
        @Size(max = 64, message = "SKU must be at most 64 characters")
        String sku,

        @NotBlank(message = "Product name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,

        @Size(max = 100, message = "Category must be at most 100 characters")
        String category,

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Unit price must be greater than or equal to 0")
        @Digits(integer = 17, fraction = 2, message = "Unit price must have at most 2 decimal places")
        BigDecimal unitPrice,

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be a 3-letter code")
        String currency,

        @NotNull(message = "Stock is required")
        @Min(value = 0, message = "Stock must be greater than or equal to 0")
        Integer stock,

        @NotNull(message = "Active status is required")
        Boolean isActive
) {
}

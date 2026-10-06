package community.store.product.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import community.store.product.domain.ProductCondition;

public record CreateProductRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 5000) String description,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @NotNull ProductCondition condition,
        @Size(max = 100) String category,
        @NotNull @Min(0) Integer stockQuantity,
        @Size(max = 10) List<@NotBlank @Size(max = 500) String> imageUrls) {
}

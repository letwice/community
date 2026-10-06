package community.store.product.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import community.store.product.domain.ProductCondition;
import community.store.product.domain.ProductStatus;

/**
 * Partial update: null fields are left unchanged. {@code imageUrls}, when present, replaces all images.
 */
public record UpdateProductRequest(
        @Size(min = 1, max = 255) String title,
        @Size(max = 5000) String description,
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal price,
        ProductCondition condition,
        @Size(max = 100) String category,
        @Min(0) Integer stockQuantity,
        ProductStatus status,
        @Size(max = 10) List<@NotBlank @Size(max = 500) String> imageUrls) {
}

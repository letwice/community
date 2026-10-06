package community.store.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(@NotBlank String productGuid, @Min(1) @Max(100) int quantity) {
}

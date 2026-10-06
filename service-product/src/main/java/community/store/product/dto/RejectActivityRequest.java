package community.store.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectActivityRequest(@NotBlank @Size(max = 500) String reason) {
}

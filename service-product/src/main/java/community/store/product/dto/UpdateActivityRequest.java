package community.store.product.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Size;

public record UpdateActivityRequest(
        @Size(min = 1, max = 255) String title,
        @Size(max = 5000) String description,
        LocalDateTime activityDate,
        @Size(max = 255) String location) {
}

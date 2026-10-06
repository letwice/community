package community.store.product.dto;

import java.time.LocalDateTime;

public record ActivityResponse(
        String guid,
        String organiserUserGuid,
        String title,
        String description,
        LocalDateTime activityDate,
        String location,
        String status,
        LocalDateTime createdAt) {
}

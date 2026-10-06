package community.store.product.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProductResponse(
        String guid,
        String vendorUserGuid,
        String title,
        String description,
        BigDecimal price,
        String condition,
        String category,
        int stockQuantity,
        String status,
        List<String> imageUrls,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}

package community.store.product.dto;

import java.time.LocalDateTime;

public record ReviewResponse(Long id, String subjectGuid, String reviewerUserGuid, int rating, String comment, LocalDateTime createdAt) {
}

package community.store.product.dto;

import java.time.LocalDateTime;

/**
 * Review as seen by an admin: includes what was reviewed and who wrote it.
 */
public record AdminReviewResponse(
        Long id,
        String subjectGuid,
        String subjectTitle,
        String reviewerUserGuid,
        String reviewerName,
        int rating,
        String comment,
        LocalDateTime createdAt) {
}

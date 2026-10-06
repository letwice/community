package community.store.notification.dto;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String userGuid,
        String type,
        String subject,
        String body,
        boolean readFlag,
        String relatedEntityType,
        String relatedEntityId,
        String dispatchStatus,
        LocalDateTime createdAt,
        LocalDateTime readAt) {
}

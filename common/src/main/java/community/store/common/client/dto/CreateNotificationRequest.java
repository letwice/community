package community.store.common.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateNotificationRequest(
        @NotBlank String userGuid,
        @NotNull String type,
        @NotBlank @Size(max = 255) String subject,
        String body,
        String relatedEntityType,
        String relatedEntityId) {
}

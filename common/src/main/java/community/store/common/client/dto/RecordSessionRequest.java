package community.store.common.client.dto;

import jakarta.validation.constraints.NotBlank;

public record RecordSessionRequest(@NotBlank String sessionJti, String ipAddress, String userAgent) {
}

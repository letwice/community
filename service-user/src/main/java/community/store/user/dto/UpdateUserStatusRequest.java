package community.store.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import community.store.user.domain.UserStatus;

public record UpdateUserStatusRequest(@NotNull UserStatus status, @Size(max = 500) String reason) {
}

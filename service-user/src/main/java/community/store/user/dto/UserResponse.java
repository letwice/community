package community.store.user.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserResponse(
        String guid,
        String email,
        String firstName,
        String lastName,
        String userType,
        String businessRegistrationNumber,
        List<String> roles,
        String status,
        String verificationStatus,
        LocalDateTime createdAt) {
}

package community.store.common.client.dto;

import java.util.List;

/**
 * Internal (service-to-service) view of a user returned by {@code service-user /system/users}.
 */
public record UserSummaryResponse(
        String guid,
        String email,
        String firstName,
        String lastName,
        String userType,
        String status,
        String verificationStatus,
        List<String> roles,
        boolean hasBankingDetails) {

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isVerified() {
        return "VERIFIED".equals(verificationStatus);
    }

    public String displayName() {
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }
}

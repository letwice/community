package community.store.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Field-level validation only. Rules that depend on {@code userType} (university email domain, vendor
 * business number) are enforced by {@link community.store.auth.service.RegistrationPolicy}.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull UserType userType,
        @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9/ -]{4,50}", message = "must be 4-50 letters, digits, spaces, '/' or '-'")
        String businessNumber) {

    /** Self-registration types; ADMIN accounts are seeded, never self-registered. */
    public enum UserType {
        STUDENT, VENDOR, COMMUNITY_LEADER, FACULTY
    }
}

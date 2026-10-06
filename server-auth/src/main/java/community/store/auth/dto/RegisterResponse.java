package community.store.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code simulatedVerificationToken} is only populated when {@code community.auth.expose-verification-token}
 * is enabled (PoC demo convenience, standing in for the email that would carry it).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegisterResponse(String userGuid, boolean verificationRequired, String simulatedVerificationToken) {
}

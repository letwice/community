package community.store.user.dto;

import java.time.LocalDateTime;

/**
 * Everything the admin user page needs in one call.
 */
public record AdminUserDetailResponse(
        UserResponse user,
        boolean hasBankingDetails,
        BankingDetailResponse bankingDetails,
        PayoutSummaryResponse payoutSummary,
        LocalDateTime lastLoginAt) {
}

package community.store.user.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PayoutSummaryResponse(
        int totalSalesCount,
        BigDecimal totalSalesAmount,
        BigDecimal totalPaidOutAmount,
        BigDecimal totalPendingPayoutAmount,
        LocalDateTime lastPayoutAt) {
}

package community.store.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import community.store.common.client.dto.PayoutDeltaRequest;

class UserPayoutSummaryTest {

    @Test
    void escrowThenReleaseMovesPendingToPaidOut() {
        UserPayoutSummary summary = new UserPayoutSummary("v");
        BigDecimal amount = new BigDecimal("150.00");
        LocalDateTime at = LocalDateTime.of(2026, 10, 5, 12, 0);

        apply(summary, PayoutDeltaRequest.escrowHeld(amount));
        apply(summary, PayoutDeltaRequest.fundsReleased(amount, at));

        assertThat(summary.getTotalSalesCount()).isEqualTo(1);
        assertThat(summary.getTotalSalesAmount()).isEqualByComparingTo("150.00");
        assertThat(summary.getTotalPaidOutAmount()).isEqualByComparingTo("150.00");
        assertThat(summary.getTotalPendingPayoutAmount()).isEqualByComparingTo("0.00");
        assertThat(summary.getLastPayoutAt()).isEqualTo(at);
    }

    @Test
    void refundReversesTheSale() {
        UserPayoutSummary summary = new UserPayoutSummary("v");
        BigDecimal amount = new BigDecimal("300.00");

        apply(summary, PayoutDeltaRequest.escrowHeld(amount));
        apply(summary, PayoutDeltaRequest.refunded(amount));

        assertThat(summary.getTotalSalesCount()).isZero();
        assertThat(summary.getTotalSalesAmount()).isEqualByComparingTo("0.00");
        assertThat(summary.getTotalPendingPayoutAmount()).isEqualByComparingTo("0.00");
    }

    private static void apply(UserPayoutSummary summary, PayoutDeltaRequest d) {
        summary.apply(d.salesCountDelta(), d.salesAmountDelta(), d.paidOutDelta(), d.pendingDelta(), d.payoutAt());
    }
}

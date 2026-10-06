package community.store.common.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Incremental change applied to a vendor's {@code user_payout_summary} rollup.
 */
public record PayoutDeltaRequest(
        int salesCountDelta,
        BigDecimal salesAmountDelta,
        BigDecimal paidOutDelta,
        BigDecimal pendingDelta,
        LocalDateTime payoutAt) {

    /** Buyer has paid into escrow: a sale is recorded and the amount is pending payout. */
    public static PayoutDeltaRequest escrowHeld(BigDecimal amount) {
        return new PayoutDeltaRequest(1, amount, BigDecimal.ZERO, amount, null);
    }

    /** Funds released to the vendor: pending moves to paid out. */
    public static PayoutDeltaRequest fundsReleased(BigDecimal amount, LocalDateTime at) {
        return new PayoutDeltaRequest(0, BigDecimal.ZERO, amount, amount.negate(), at);
    }

    /** Escrowed sale refunded to the buyer: the sale is reversed. */
    public static PayoutDeltaRequest refunded(BigDecimal amount) {
        return new PayoutDeltaRequest(-1, amount.negate(), BigDecimal.ZERO, amount.negate(), null);
    }
}

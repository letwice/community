package community.store.user.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Denormalised dashboard rollup, maintained by deltas pushed from service-product.
 */
@Entity
@Table(name = "user_payout_summary")
public class UserPayoutSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_guid", nullable = false, unique = true, length = 36)
    private String userGuid;

    @Column(name = "total_sales_count", nullable = false)
    private int totalSalesCount;

    @Column(name = "total_sales_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalSalesAmount = BigDecimal.ZERO;

    @Column(name = "total_paid_out_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPaidOutAmount = BigDecimal.ZERO;

    @Column(name = "total_pending_payout_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPendingPayoutAmount = BigDecimal.ZERO;

    @Column(name = "last_payout_at")
    private LocalDateTime lastPayoutAt;

    protected UserPayoutSummary() {
    }

    public UserPayoutSummary(String userGuid) {
        this.userGuid = userGuid;
    }

    public void apply(int salesCountDelta, BigDecimal salesAmountDelta, BigDecimal paidOutDelta, BigDecimal pendingDelta, LocalDateTime payoutAt) {
        totalSalesCount += salesCountDelta;
        totalSalesAmount = totalSalesAmount.add(orZero(salesAmountDelta));
        totalPaidOutAmount = totalPaidOutAmount.add(orZero(paidOutDelta));
        totalPendingPayoutAmount = totalPendingPayoutAmount.add(orZero(pendingDelta));
        if (payoutAt != null) {
            lastPayoutAt = payoutAt;
        }
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public String getUserGuid() {
        return userGuid;
    }

    public int getTotalSalesCount() {
        return totalSalesCount;
    }

    public BigDecimal getTotalSalesAmount() {
        return totalSalesAmount;
    }

    public BigDecimal getTotalPaidOutAmount() {
        return totalPaidOutAmount;
    }

    public BigDecimal getTotalPendingPayoutAmount() {
        return totalPendingPayoutAmount;
    }

    public LocalDateTime getLastPayoutAt() {
        return lastPayoutAt;
    }
}

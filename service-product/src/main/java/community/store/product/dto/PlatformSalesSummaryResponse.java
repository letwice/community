package community.store.product.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Admin sales dashboard: platform totals plus one row per seller.
 */
public record PlatformSalesSummaryResponse(
        long totalOrders,
        Map<String, Long> ordersByStatus,
        BigDecimal totalSalesAmount,
        BigDecimal totalPaidOutAmount,
        BigDecimal totalPendingPayoutAmount,
        BigDecimal totalRefundedAmount,
        long ordersAwaitingRelease,
        long eventsAwaitingApproval,
        List<VendorSalesRow> vendors) {

    public record VendorSalesRow(
            String vendorUserGuid,
            String vendorName,
            String accountStatus,
            long totalOrders,
            BigDecimal totalSalesAmount,
            BigDecimal totalPaidOutAmount,
            BigDecimal totalPendingPayoutAmount,
            BigDecimal totalRefundedAmount,
            long ordersAwaitingRelease,
            long activeListings,
            Double averageRating,
            long reviewCount,
            boolean lowRating) {
    }
}

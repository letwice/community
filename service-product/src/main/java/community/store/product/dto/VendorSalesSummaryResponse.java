package community.store.product.dto;

import java.math.BigDecimal;
import java.util.Map;

public record VendorSalesSummaryResponse(
        String vendorUserGuid,
        String vendorName,
        long totalOrders,
        Map<String, Long> ordersByStatus,
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

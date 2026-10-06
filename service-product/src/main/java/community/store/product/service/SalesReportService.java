package community.store.product.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import community.store.common.client.UserClient;
import community.store.common.client.dto.UserSummaryResponse;
import community.store.product.config.MarketplaceProperties;
import community.store.product.domain.ActivityStatus;
import community.store.product.domain.CommunityActivityRepository;
import community.store.product.domain.EscrowStatus;
import community.store.product.domain.GuidRatingStats;
import community.store.product.domain.OrderTransactionRepository;
import community.store.product.domain.OrderTransactionRepository.StatusTotal;
import community.store.product.domain.OrderTransactionRepository.VendorStatusTotal;
import community.store.product.domain.ProductRepository;
import community.store.product.domain.ProductReviewRepository;
import community.store.product.domain.ProductStatus;
import community.store.product.domain.RatingStats;
import community.store.product.dto.PlatformSalesSummaryResponse;
import community.store.product.dto.PlatformSalesSummaryResponse.VendorSalesRow;
import community.store.product.dto.VendorSalesSummaryResponse;

/**
 * Admin sales reporting: platform-wide totals, a row per seller, and a single seller's summary.
 * Sales count escrowed and released orders; cancelled orders are excluded and refunds are reported separately.
 */
@Service
public class SalesReportService {

    private final OrderTransactionRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductReviewRepository reviewRepository;
    private final CommunityActivityRepository activityRepository;
    private final UserClient userClient;
    private final MarketplaceProperties properties;

    public SalesReportService(OrderTransactionRepository orderRepository,
                              ProductRepository productRepository,
                              ProductReviewRepository reviewRepository,
                              CommunityActivityRepository activityRepository,
                              UserClient userClient,
                              MarketplaceProperties properties) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.activityRepository = activityRepository;
        this.userClient = userClient;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PlatformSalesSummaryResponse platformSummary() {
        List<VendorStatusTotal> rows = orderRepository.totalsByVendorAndStatus();
        Map<String, Long> activeListings = productRepository.countByVendorForStatus(ProductStatus.ACTIVE).stream()
                .collect(Collectors.toMap(ProductRepository.GuidCount::getGuid, ProductRepository.GuidCount::getTotal));
        Map<String, GuidRatingStats> ratings = reviewRepository.statsByVendor().stream()
                .collect(Collectors.toMap(GuidRatingStats::getGuid, Function.identity()));

        Set<String> vendorGuids = new HashSet<>(activeListings.keySet());
        rows.forEach(r -> vendorGuids.add(r.getVendorGuid()));
        Map<String, UserSummaryResponse> users = userClient.getUsers(vendorGuids);

        Totals platform = new Totals();
        Map<String, Totals> perVendor = new LinkedHashMap<>();
        for (VendorStatusTotal row : rows) {
            platform.add(row);
            perVendor.computeIfAbsent(row.getVendorGuid(), g -> new Totals()).add(row);
        }

        List<VendorSalesRow> vendors = new ArrayList<>();
        for (String guid : vendorGuids) {
            Totals t = perVendor.getOrDefault(guid, new Totals());
            RatingStats rating = ratings.get(guid);
            Double average = rating == null ? null : rating.average();
            long reviewCount = rating == null ? 0 : rating.getReviewCount();
            UserSummaryResponse user = users.get(guid);
            vendors.add(new VendorSalesRow(guid, user == null ? "Unknown user" : user.displayName(),
                    user == null ? null : user.status(), t.orders, t.sales, t.paidOut, t.pending, t.refunded,
                    t.awaitingAdmin, activeListings.getOrDefault(guid, 0L), average, reviewCount,
                    properties.isLowRating(average, reviewCount)));
        }
        vendors.sort(Comparator.comparing(VendorSalesRow::totalSalesAmount).reversed().thenComparing(VendorSalesRow::vendorName));

        return new PlatformSalesSummaryResponse(platform.orders, platform.byStatus, platform.sales, platform.paidOut,
                platform.pending, platform.refunded, platform.awaitingAdmin,
                activityRepository.countByStatus(ActivityStatus.PENDING_APPROVAL), vendors);
    }

    @Transactional(readOnly = true)
    public VendorSalesSummaryResponse vendorSummary(String vendorGuid) {
        UserSummaryResponse vendor = userClient.getUser(vendorGuid);
        Totals t = new Totals();
        orderRepository.totalsByStatus(vendorGuid).forEach(t::add);
        RatingStats rating = reviewRepository.statsForVendor(vendorGuid);
        Double average = rating.average();
        return new VendorSalesSummaryResponse(vendorGuid, vendor.displayName(), t.orders, t.byStatus, t.sales, t.paidOut,
                t.pending, t.refunded, t.awaitingAdmin,
                productRepository.countByVendorUserGuidAndStatus(vendorGuid, ProductStatus.ACTIVE),
                average, rating.getReviewCount(), properties.isLowRating(average, rating.getReviewCount()));
    }

    /** Accumulates order totals by escrow status. */
    private static final class Totals {
        private final Map<String, Long> byStatus = new LinkedHashMap<>();
        private long orders;
        private long awaitingAdmin;
        private BigDecimal sales = BigDecimal.ZERO;
        private BigDecimal paidOut = BigDecimal.ZERO;
        private BigDecimal pending = BigDecimal.ZERO;
        private BigDecimal refunded = BigDecimal.ZERO;

        void add(StatusTotal row) {
            EscrowStatus status = row.getStatus();
            BigDecimal amount = row.getTotalAmount() == null ? BigDecimal.ZERO : row.getTotalAmount();
            byStatus.merge(status.name(), row.getOrderCount(), Long::sum);
            orders += row.getOrderCount();
            if (status == EscrowStatus.FUNDS_RELEASED) {
                paidOut = paidOut.add(amount);
                sales = sales.add(amount);
            } else if (EscrowStatus.pendingPayout().contains(status)) {
                pending = pending.add(amount);
                sales = sales.add(amount);
            } else if (status == EscrowStatus.REFUNDED) {
                refunded = refunded.add(amount);
            }
            if (EscrowStatus.awaitingAdmin().contains(status)) {
                awaitingAdmin += row.getOrderCount();
            }
        }
    }
}

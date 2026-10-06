package community.store.product.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import community.store.common.web.PageResponse;
import community.store.product.domain.ActivityStatus;
import community.store.product.domain.EscrowStatus;
import community.store.product.dto.ActivityResponse;
import community.store.product.dto.AdminActivityResponse;
import community.store.product.dto.AdminReviewResponse;
import community.store.product.dto.OrderResponse;
import community.store.product.dto.PlatformSalesSummaryResponse;
import community.store.product.dto.ProductResponse;
import community.store.product.dto.RejectActivityRequest;
import community.store.product.dto.ResolveDisputeRequest;
import community.store.product.dto.VendorSalesSummaryResponse;
import community.store.product.service.ActivityService;
import community.store.product.service.OrderService;
import community.store.product.service.ProductService;
import community.store.product.service.ReviewService;
import community.store.product.service.SalesReportService;

@RestController
@RequestMapping("/backoffice")
@PreAuthorize("hasRole('ADMIN')")
public class BackofficeController {

    private final OrderService orderService;
    private final SalesReportService salesReportService;
    private final ProductService productService;
    private final ReviewService reviewService;
    private final ActivityService activityService;

    public BackofficeController(OrderService orderService, SalesReportService salesReportService, ProductService productService,
                                ReviewService reviewService, ActivityService activityService) {
        this.orderService = orderService;
        this.salesReportService = salesReportService;
        this.productService = productService;
        this.reviewService = reviewService;
        this.activityService = activityService;
    }

    // --- sales ----------------------------------------------------------------------------

    /** Platform totals plus one row per seller (sales, payouts, refunds, listings, rating). */
    @GetMapping("/sales-summary")
    public PlatformSalesSummaryResponse platformSalesSummary() {
        return salesReportService.platformSummary();
    }

    @GetMapping("/vendors/{guid}/sales-summary")
    public VendorSalesSummaryResponse salesSummary(@PathVariable String guid) {
        return salesReportService.vendorSummary(guid);
    }

    @GetMapping("/vendors/{guid}/products")
    public PageResponse<ProductResponse> vendorProducts(@PathVariable String guid,
                                                        @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return productService.listMine(guid, pageable);
    }

    @GetMapping("/vendors/{guid}/reviews")
    public PageResponse<AdminReviewResponse> vendorReviews(@PathVariable String guid,
                                                           @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return reviewService.listVendorReviews(guid, pageable);
    }

    // --- orders ---------------------------------------------------------------------------

    /** {@code status} may repeat ({@code ?status=DELIVERED_CONFIRMED&status=DISPUTED}); {@code vendor} filters by seller guid. */
    @GetMapping("/orders")
    public PageResponse<OrderResponse> orders(@RequestParam(required = false) List<EscrowStatus> status,
                                              @RequestParam(required = false) String vendor,
                                              @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.listAll(status, vendor, pageable);
    }

    /** Release escrowed funds to the seller (receipt confirmed, dispute in the seller's favour, or shipped and unconfirmed). */
    @PostMapping("/orders/{guid}/release")
    public OrderResponse release(@PathVariable String guid) {
        return orderService.adminRelease(guid);
    }

    /** Manual dispute resolution (DISPUTED -> REFUNDED or FUNDS_RELEASED). */
    @PostMapping("/orders/{guid}/resolve")
    public OrderResponse resolve(@PathVariable String guid, @Valid @RequestBody ResolveDisputeRequest request) {
        return orderService.resolveDispute(guid, request.outcome());
    }

    // --- community events -----------------------------------------------------------------

    @GetMapping("/activities")
    public PageResponse<AdminActivityResponse> activities(@RequestParam(required = false) ActivityStatus status,
                                                          @PageableDefault(size = 50) Pageable pageable) {
        return activityService.adminList(status, pageable);
    }

    @GetMapping("/activities/{guid}/reviews")
    public PageResponse<AdminReviewResponse> activityReviews(@PathVariable String guid,
                                                             @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return activityService.adminReviews(guid, pageable);
    }

    @PostMapping("/activities/{guid}/approve")
    public ActivityResponse approve(@PathVariable String guid) {
        return activityService.approve(guid);
    }

    @PostMapping("/activities/{guid}/reject")
    public ActivityResponse reject(@PathVariable String guid, @Valid @RequestBody RejectActivityRequest request) {
        return activityService.reject(guid, request.reason());
    }
}

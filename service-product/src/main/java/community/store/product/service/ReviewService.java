package community.store.product.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import community.store.common.client.UserClient;
import community.store.common.client.dto.UserSummaryResponse;
import community.store.common.web.ApiException;
import community.store.common.web.PageResponse;
import community.store.product.domain.EscrowStatus;
import community.store.product.domain.OrderTransaction;
import community.store.product.domain.OrderTransactionRepository;
import community.store.product.domain.Product;
import community.store.product.domain.ProductRepository;
import community.store.product.domain.ProductReview;
import community.store.product.domain.ProductReviewRepository;
import community.store.product.dto.AdminReviewResponse;
import community.store.product.dto.CreateReviewRequest;
import community.store.product.dto.ReviewResponse;

@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private static final Set<EscrowStatus> REVIEWABLE = EnumSet.of(EscrowStatus.DELIVERED_CONFIRMED, EscrowStatus.FUNDS_RELEASED);

    private final ProductReviewRepository reviewRepository;
    private final OrderTransactionRepository orderRepository;
    private final ProductRepository productRepository;
    private final RatingWatch ratingWatch;
    private final UserClient userClient;

    public ReviewService(ProductReviewRepository reviewRepository, OrderTransactionRepository orderRepository,
                         ProductRepository productRepository, RatingWatch ratingWatch, UserClient userClient) {
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.ratingWatch = ratingWatch;
        this.userClient = userClient;
    }

    /**
     * Only the buyer of an order for this product may review it, once per order, after confirming receipt
     * (they don't have to wait for the admin to release the funds).
     */
    @Transactional
    public ReviewResponse createProductReview(String reviewerGuid, String productGuid, CreateReviewRequest request) {
        OrderTransaction order = orderRepository.findByGuid(request.orderGuid())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!order.isBuyer(reviewerGuid)) {
            throw ApiException.forbidden("You can only review products you bought");
        }
        if (!order.getProductGuid().equals(productGuid)) {
            throw ApiException.badRequest("Order is not for this product");
        }
        if (!REVIEWABLE.contains(order.getEscrowStatus())) {
            throw ApiException.conflict("You can review this product once you have confirmed receipt");
        }
        if (reviewRepository.existsByOrderTransactionGuid(order.getGuid())) {
            throw ApiException.conflict("You have already reviewed this order");
        }
        ProductReview review = reviewRepository.save(
                new ProductReview(productGuid, reviewerGuid, order.getGuid(), request.rating(), request.comment()));
        log.info("User {} reviewed product {} ({} stars)", reviewerGuid, productGuid, request.rating());
        ratingWatch.afterReview(order.getVendorUserGuid(), "Seller", reviewRepository.statsForVendor(order.getVendorUserGuid()),
                request.rating());
        return toResponse(review);
    }

    /** Admin view of every review on a seller's products, with product titles and reviewer names. */
    @Transactional(readOnly = true)
    public PageResponse<AdminReviewResponse> listVendorReviews(String vendorGuid, Pageable pageable) {
        Page<ProductReview> page = reviewRepository.findByVendor(vendorGuid, pageable);
        Map<String, String> titles = productRepository.findByGuidIn(page.map(ProductReview::getProductGuid).toSet()).stream()
                .collect(Collectors.toMap(Product::getGuid, Product::getTitle));
        Map<String, UserSummaryResponse> reviewers = userClient.getUsers(page.map(ProductReview::getReviewerUserGuid).toSet());
        return PageResponse.from(page, r -> new AdminReviewResponse(r.getId(), r.getProductGuid(), titles.get(r.getProductGuid()),
                r.getReviewerUserGuid(), nameOf(reviewers.get(r.getReviewerUserGuid())), r.getRating(), r.getComment(), r.getCreatedAt()));
    }

    static String nameOf(UserSummaryResponse user) {
        return user == null ? "Unknown user" : user.displayName();
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listProductReviews(String productGuid, Pageable pageable) {
        return PageResponse.from(reviewRepository.findByProductGuid(productGuid, pageable), ReviewService::toResponse);
    }

    private static ReviewResponse toResponse(ProductReview r) {
        return new ReviewResponse(r.getId(), r.getProductGuid(), r.getReviewerUserGuid(), r.getRating(), r.getComment(), r.getCreatedAt());
    }
}

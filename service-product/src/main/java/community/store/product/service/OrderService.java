package community.store.product.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import community.store.common.client.AdminNotifier;
import community.store.common.client.NotificationClient;
import community.store.common.client.UserClient;
import community.store.common.client.dto.PayoutDeltaRequest;
import community.store.common.web.ApiException;
import community.store.common.web.PageResponse;
import community.store.product.config.MarketplaceProperties;
import community.store.product.domain.EscrowStatus;
import community.store.product.domain.OrderTransaction;
import community.store.product.domain.OrderTransactionRepository;
import community.store.product.domain.Product;
import community.store.product.domain.ProductRepository;
import community.store.product.domain.ProductReviewRepository;
import community.store.product.dto.CreateOrderRequest;
import community.store.product.dto.OrderResponse;
import community.store.product.dto.ResolveDisputeRequest;

/**
 * Simulated EFT + escrow. No money moves: {@code confirm-payment} is a buyer self-attestation, and
 * {@code confirm-receipt} is the only (conceptual) release of funds, mirrored into the vendor's payout summary.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderTransactionRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductReviewRepository reviewRepository;
    private final AccountChecks accountChecks;
    private final UserClient userClient;
    private final NotificationClient notificationClient;
    private final AdminNotifier adminNotifier;
    private final MarketplaceProperties properties;
    private final Clock clock;

    public OrderService(OrderTransactionRepository orderRepository,
                        ProductRepository productRepository,
                        ProductReviewRepository reviewRepository,
                        AccountChecks accountChecks,
                        UserClient userClient,
                        NotificationClient notificationClient,
                        AdminNotifier adminNotifier,
                        MarketplaceProperties properties,
                        Clock clock) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.accountChecks = accountChecks;
        this.userClient = userClient;
        this.notificationClient = notificationClient;
        this.adminNotifier = adminNotifier;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse create(String buyerGuid, CreateOrderRequest request) {
        Product product = productRepository.findByGuidForUpdate(request.productGuid())
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        if (product.isOwnedBy(buyerGuid)) {
            throw ApiException.badRequest("You cannot buy your own product");
        }
        accountChecks.requireActive(product.getVendorUserGuid(), "The seller is not currently accepting orders");
        product.reserve(request.quantity());

        OrderTransaction order = new OrderTransaction(buyerGuid, product, request.quantity());
        order.transitionTo(EscrowStatus.AWAITING_PAYMENT, now());
        orderRepository.saveAndFlush(order);
        order.assignEscrowReference();
        logTransition(order, EscrowStatus.CREATED);

        notificationClient.inbox(order.getVendorUserGuid(), "New order " + order.getEscrowReferenceNumber(),
                "You have a new order for " + order.getQuantity() + " x " + product.getTitle()
                        + ". Awaiting the buyer's (simulated) EFT payment into escrow.",
                NotificationClient.ENTITY_ORDER, order.getGuid());
        return toResponse(order, product.getTitle(), false);
    }

    @Transactional
    public OrderResponse confirmPayment(String buyerGuid, String orderGuid) {
        OrderTransaction order = loadForBuyer(buyerGuid, orderGuid);
        transition(order, EscrowStatus.ESCROW_HELD);
        userClient.applyPayoutDelta(order.getVendorUserGuid(), PayoutDeltaRequest.escrowHeld(order.getTotalAmount()));
        notificationClient.inbox(order.getVendorUserGuid(), "Payment received for " + order.getEscrowReferenceNumber(),
                "[SIMULATED] The buyer has paid R" + order.getTotalAmount() + " into escrow. Please ship the order.",
                NotificationClient.ENTITY_ORDER, order.getGuid());
        return toResponse(order);
    }

    @Transactional
    public OrderResponse markShipped(String vendorGuid, String orderGuid) {
        OrderTransaction order = loadForVendor(vendorGuid, orderGuid);
        transition(order, EscrowStatus.SHIPPED);
        notificationClient.inbox(order.getBuyerUserGuid(), "Order " + order.getEscrowReferenceNumber() + " shipped",
                "Your order is on its way. Confirm receipt once it arrives to release funds to the seller.",
                NotificationClient.ENTITY_ORDER, order.getGuid());
        return toResponse(order);
    }

    /**
     * Buyer confirms receipt. Unless {@code community.marketplace.auto-release-funds} is on, the funds then wait
     * for an admin to release them, and every admin is notified.
     */
    @Transactional
    public OrderResponse confirmReceipt(String buyerGuid, String orderGuid) {
        OrderTransaction order = loadForBuyer(buyerGuid, orderGuid);
        transition(order, EscrowStatus.DELIVERED_CONFIRMED);
        if (properties.isAutoReleaseFunds()) {
            releaseFunds(order, false);
        } else {
            notificationClient.inbox(order.getVendorUserGuid(), "Receipt confirmed for " + order.getEscrowReferenceNumber(),
                    "The buyer confirmed receipt. Funds will be released to you once an admin approves the payout.",
                    NotificationClient.ENTITY_ORDER, order.getGuid());
            adminNotifier.notifyAdmins("Release funds for " + order.getEscrowReferenceNumber(),
                    "The buyer confirmed receipt of " + order.getQuantity() + " item(s) worth R" + order.getTotalAmount()
                            + ". Review the order and release the funds to the seller.",
                    NotificationClient.ENTITY_ORDER, order.getGuid());
        }
        return toResponse(order);
    }

    /**
     * Admin releases escrowed funds to the seller: after receipt is confirmed, to settle a dispute in the seller's
     * favour, or for a shipped order the buyer never confirmed.
     */
    @Transactional
    public OrderResponse adminRelease(String orderGuid) {
        OrderTransaction order = orderRepository.findByGuidForUpdate(orderGuid)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        switch (order.getEscrowStatus()) {
            case SHIPPED -> transition(order, EscrowStatus.DELIVERED_CONFIRMED);
            case DELIVERED_CONFIRMED, DISPUTED -> {
                // can be released directly
            }
            default -> throw ApiException.conflict("Funds for an order in " + order.getEscrowStatus() + " cannot be released");
        }
        releaseFunds(order, true);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse cancel(String userGuid, String orderGuid) {
        OrderTransaction order = orderRepository.findByGuidForUpdate(orderGuid)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!order.isBuyer(userGuid) && !order.isVendor(userGuid)) {
            throw ApiException.forbidden("You are not a party to this order");
        }
        transition(order, EscrowStatus.CANCELLED);
        productRepository.findByGuidForUpdate(order.getProductGuid()).ifPresent(p -> p.release(order.getQuantity()));
        String otherParty = order.isBuyer(userGuid) ? order.getVendorUserGuid() : order.getBuyerUserGuid();
        notificationClient.inbox(otherParty, "Order " + order.getEscrowReferenceNumber() + " cancelled",
                "The order was cancelled before payment.", NotificationClient.ENTITY_ORDER, order.getGuid());
        return toResponse(order);
    }

    @Transactional
    public OrderResponse dispute(String buyerGuid, String orderGuid) {
        OrderTransaction order = loadForBuyer(buyerGuid, orderGuid);
        transition(order, EscrowStatus.DISPUTED);
        notificationClient.inbox(order.getVendorUserGuid(), "Order " + order.getEscrowReferenceNumber() + " disputed",
                "The buyer has raised a dispute. Funds stay in escrow until an admin resolves it.",
                NotificationClient.ENTITY_ORDER, order.getGuid());
        adminNotifier.notifyAdmins("Dispute on " + order.getEscrowReferenceNumber(),
                "The buyer disputed an order worth R" + order.getTotalAmount() + ". Refund the buyer or release the funds to the seller.",
                NotificationClient.ENTITY_ORDER, order.getGuid());
        return toResponse(order);
    }

    /** Manual admin resolution of a dispute (refund the buyer or release to the vendor). */
    @Transactional
    public OrderResponse resolveDispute(String orderGuid, ResolveDisputeRequest.Outcome outcome) {
        OrderTransaction order = orderRepository.findByGuidForUpdate(orderGuid)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (order.getEscrowStatus() != EscrowStatus.DISPUTED) {
            throw ApiException.conflict("Only disputed orders can be resolved");
        }
        if (outcome == ResolveDisputeRequest.Outcome.RELEASE) {
            releaseFunds(order, true);
        } else {
            transition(order, EscrowStatus.REFUNDED);
            userClient.applyPayoutDelta(order.getVendorUserGuid(), PayoutDeltaRequest.refunded(order.getTotalAmount()));
            String message = "[SIMULATED] Dispute resolved: R" + order.getTotalAmount() + " refunded to the buyer.";
            notificationClient.inbox(order.getBuyerUserGuid(), "Order " + order.getEscrowReferenceNumber() + " refunded",
                    message, NotificationClient.ENTITY_ORDER, order.getGuid());
            notificationClient.inbox(order.getVendorUserGuid(), "Order " + order.getEscrowReferenceNumber() + " refunded",
                    message, NotificationClient.ENTITY_ORDER, order.getGuid());
        }
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(String userGuid, boolean admin, String orderGuid) {
        OrderTransaction order = orderRepository.findByGuid(orderGuid)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!admin && !order.isBuyer(userGuid) && !order.isVendor(userGuid)) {
            throw ApiException.notFound("Order not found");
        }
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listMine(String userGuid, String role, EscrowStatus status, Pageable pageable) {
        Page<OrderTransaction> page = switch (role == null ? "buyer" : role) {
            case "buyer" -> orderRepository.findForBuyer(userGuid, status, pageable);
            case "vendor" -> orderRepository.findForVendor(userGuid, status, pageable);
            default -> throw ApiException.badRequest("role must be buyer or vendor");
        };
        return toPage(page);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listAll(Collection<EscrowStatus> statuses, String vendorGuid, Pageable pageable) {
        boolean anyStatus = statuses == null || statuses.isEmpty();
        return toPage(orderRepository.searchAll(anyStatus, anyStatus ? EnumSet.allOf(EscrowStatus.class) : statuses,
                vendorGuid == null || vendorGuid.isBlank() ? null : vendorGuid, pageable));
    }

    private void releaseFunds(OrderTransaction order, boolean byAdmin) {
        transition(order, EscrowStatus.FUNDS_RELEASED);
        userClient.applyPayoutDelta(order.getVendorUserGuid(), PayoutDeltaRequest.fundsReleased(order.getTotalAmount(), now()));
        notificationClient.inbox(order.getVendorUserGuid(), "Funds released for " + order.getEscrowReferenceNumber(),
                "[SIMULATED] R" + order.getTotalAmount() + " has been released from escrow to your payout balance.",
                NotificationClient.ENTITY_ORDER, order.getGuid());
        if (byAdmin) {
            notificationClient.inbox(order.getBuyerUserGuid(), "Order " + order.getEscrowReferenceNumber() + " completed",
                    "An admin released the escrowed funds to the seller.", NotificationClient.ENTITY_ORDER, order.getGuid());
        }
    }

    private void transition(OrderTransaction order, EscrowStatus target) {
        EscrowStatus from = order.getEscrowStatus();
        order.transitionTo(target, now());
        logTransition(order, from);
    }

    private void logTransition(OrderTransaction order, EscrowStatus from) {
        log.info("Order {} ({}) escrow status {} -> {}", order.getEscrowReferenceNumber(), order.getGuid(), from, order.getEscrowStatus());
    }

    private OrderTransaction loadForBuyer(String buyerGuid, String orderGuid) {
        OrderTransaction order = orderRepository.findByGuidForUpdate(orderGuid)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!order.isBuyer(buyerGuid)) {
            throw ApiException.forbidden("Only the buyer can perform this action");
        }
        return order;
    }

    private OrderTransaction loadForVendor(String vendorGuid, String orderGuid) {
        OrderTransaction order = orderRepository.findByGuidForUpdate(orderGuid)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!order.isVendor(vendorGuid)) {
            throw ApiException.forbidden("Only the seller can perform this action");
        }
        return order;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private OrderResponse toResponse(OrderTransaction order) {
        String title = productRepository.findByGuid(order.getProductGuid()).map(Product::getTitle).orElse(null);
        return toResponse(order, title, reviewRepository.existsByOrderTransactionGuid(order.getGuid()));
    }

    private PageResponse<OrderResponse> toPage(Page<OrderTransaction> page) {
        List<OrderTransaction> orders = page.getContent();
        Map<String, String> titles = productRepository.findByGuidIn(orders.stream().map(OrderTransaction::getProductGuid).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Product::getGuid, Product::getTitle));
        Set<String> reviewed = orders.isEmpty() ? Set.of()
                : new HashSet<>(reviewRepository.findReviewedOrderGuids(orders.stream().map(OrderTransaction::getGuid).toList()));
        Function<OrderTransaction, OrderResponse> mapper =
                o -> toResponse(o, titles.get(o.getProductGuid()), reviewed.contains(o.getGuid()));
        return PageResponse.from(page, mapper);
    }

    private static OrderResponse toResponse(OrderTransaction o, String productTitle, boolean reviewed) {
        return new OrderResponse(o.getGuid(), o.getBuyerUserGuid(), o.getVendorUserGuid(), o.getProductGuid(), productTitle,
                o.getQuantity(), o.getUnitPrice(), o.getTotalAmount(), o.getEscrowStatus().name(), o.getEscrowReferenceNumber(),
                o.getPaidAt(), o.getShippedAt(), o.getDeliveryConfirmedAt(), o.getFundsReleasedAt(), o.getCreatedAt(), reviewed);
    }
}

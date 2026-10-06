package community.store.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import community.store.common.client.AdminNotifier;
import community.store.common.client.NotificationClient;
import community.store.common.client.UserClient;
import community.store.common.client.dto.PayoutDeltaRequest;
import community.store.common.web.ApiException;
import community.store.product.config.MarketplaceProperties;
import community.store.product.domain.EscrowStatus;
import community.store.product.domain.OrderTransaction;
import community.store.product.domain.OrderTransactionRepository;
import community.store.product.domain.Product;
import community.store.product.domain.ProductCondition;
import community.store.product.domain.ProductRepository;
import community.store.product.domain.ProductReviewRepository;
import community.store.product.dto.CreateOrderRequest;
import community.store.product.dto.OrderResponse;
import community.store.product.dto.ResolveDisputeRequest;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final String BUYER = "buyer-guid";
    private static final String VENDOR = "vendor-guid";

    @Mock
    private OrderTransactionRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductReviewRepository reviewRepository;
    @Mock
    private AccountChecks accountChecks;
    @Mock
    private UserClient userClient;
    @Mock
    private NotificationClient notificationClient;
    @Mock
    private AdminNotifier adminNotifier;

    private final MarketplaceProperties properties = new MarketplaceProperties();
    private OrderService service;
    private Product product;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T10:00:00Z"), ZoneOffset.UTC);
        service = new OrderService(orderRepository, productRepository, reviewRepository, accountChecks, userClient, notificationClient,
                adminNotifier, properties, clock);
        product = new Product(VENDOR, "Textbook", "desc", new BigDecimal("150.00"), ProductCondition.SECOND_HAND, "Textbooks", 2);
    }

    @Test
    void createOrderReservesStockAndAwaitsPayment() {
        when(productRepository.findByGuidForUpdate(product.getGuid())).thenReturn(Optional.of(product));
        when(orderRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = service.create(BUYER, new CreateOrderRequest(product.getGuid(), 2));

        assertThat(response.escrowStatus()).isEqualTo("AWAITING_PAYMENT");
        assertThat(response.totalAmount()).isEqualByComparingTo("300.00");
        assertThat(product.getStockQuantity()).isZero();
        verify(notificationClient).inbox(eq(VENDOR), anyString(), anyString(), eq(NotificationClient.ENTITY_ORDER), anyString());
    }

    @Test
    void vendorCannotBuyOwnProduct() {
        when(productRepository.findByGuidForUpdate(product.getGuid())).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.create(VENDOR, new CreateOrderRequest(product.getGuid(), 1)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void confirmPaymentMovesToEscrowHeldAndRecordsPendingPayout() {
        OrderTransaction order = orderIn(EscrowStatus.AWAITING_PAYMENT);
        when(orderRepository.findByGuidForUpdate(order.getGuid())).thenReturn(Optional.of(order));

        OrderResponse response = service.confirmPayment(BUYER, order.getGuid());

        assertThat(response.escrowStatus()).isEqualTo("ESCROW_HELD");
        ArgumentCaptor<PayoutDeltaRequest> delta = ArgumentCaptor.forClass(PayoutDeltaRequest.class);
        verify(userClient).applyPayoutDelta(eq(VENDOR), delta.capture());
        assertThat(delta.getValue().salesCountDelta()).isEqualTo(1);
        assertThat(delta.getValue().pendingDelta()).isEqualByComparingTo("150.00");
    }

    @Test
    void onlyBuyerCanConfirmPayment() {
        OrderTransaction order = orderIn(EscrowStatus.AWAITING_PAYMENT);
        when(orderRepository.findByGuidForUpdate(order.getGuid())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.confirmPayment(VENDOR, order.getGuid()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(userClient, never()).applyPayoutDelta(anyString(), any());
    }

    @Test
    void vendorCannotShipUnpaidOrder() {
        OrderTransaction order = orderIn(EscrowStatus.AWAITING_PAYMENT);
        when(orderRepository.findByGuidForUpdate(order.getGuid())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.markShipped(VENDOR, order.getGuid()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void confirmReceiptWaitsForAdminAndAlertsAdmins() {
        OrderTransaction order = orderIn(EscrowStatus.SHIPPED);
        when(orderRepository.findByGuidForUpdate(order.getGuid())).thenReturn(Optional.of(order));

        OrderResponse response = service.confirmReceipt(BUYER, order.getGuid());

        assertThat(response.escrowStatus()).isEqualTo("DELIVERED_CONFIRMED");
        verify(userClient, never()).applyPayoutDelta(anyString(), any());
        verify(adminNotifier).notifyAdmins(startsWith("Release funds"), anyString(), eq(NotificationClient.ENTITY_ORDER), eq(order.getGuid()));
    }

    @Test
    void adminReleaseAfterReceiptMovesPendingToPaidOut() {
        OrderTransaction order = orderIn(EscrowStatus.SHIPPED);
        order.transitionTo(EscrowStatus.DELIVERED_CONFIRMED, null);
        when(orderRepository.findByGuidForUpdate(order.getGuid())).thenReturn(Optional.of(order));

        OrderResponse response = service.adminRelease(order.getGuid());

        assertThat(response.escrowStatus()).isEqualTo("FUNDS_RELEASED");
        ArgumentCaptor<PayoutDeltaRequest> delta = ArgumentCaptor.forClass(PayoutDeltaRequest.class);
        verify(userClient).applyPayoutDelta(eq(VENDOR), delta.capture());
        assertThat(delta.getValue().paidOutDelta()).isEqualByComparingTo("150.00");
        assertThat(delta.getValue().pendingDelta()).isEqualByComparingTo("-150.00");
        verify(notificationClient).inbox(eq(BUYER), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void adminCanReleaseShippedOrderTheBuyerNeverConfirmed() {
        OrderTransaction order = orderIn(EscrowStatus.SHIPPED);
        when(orderRepository.findByGuidForUpdate(order.getGuid())).thenReturn(Optional.of(order));

        service.adminRelease(order.getGuid());

        assertThat(order.getEscrowStatus()).isEqualTo(EscrowStatus.FUNDS_RELEASED);
        assertThat(order.getDeliveryConfirmedAt()).isNotNull();
    }

    @Test
    void adminCannotReleaseBeforeShipping() {
        OrderTransaction order = orderIn(EscrowStatus.ESCROW_HELD);
        when(orderRepository.findByGuidForUpdate(order.getGuid())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.adminRelease(order.getGuid()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(userClient, never()).applyPayoutDelta(anyString(), any());
    }

    @Test
    void disputeAlertsAdmins() {
        OrderTransaction order = orderIn(EscrowStatus.ESCROW_HELD);
        when(orderRepository.findByGuidForUpdate(order.getGuid())).thenReturn(Optional.of(order));

        service.dispute(BUYER, order.getGuid());

        verify(adminNotifier).notifyAdmins(startsWith("Dispute on"), anyString(), eq(NotificationClient.ENTITY_ORDER), eq(order.getGuid()));
    }

    @Test
    void autoReleaseModeReleasesFundsOnConfirmation() {
        properties.setAutoReleaseFunds(true);
        OrderTransaction order = orderIn(EscrowStatus.SHIPPED);
        when(orderRepository.findByGuidForUpdate(order.getGuid())).thenReturn(Optional.of(order));

        OrderResponse response = service.confirmReceipt(BUYER, order.getGuid());

        assertThat(response.escrowStatus()).isEqualTo("FUNDS_RELEASED");
        assertThat(order.getDeliveryConfirmedAt()).isNotNull();
        assertThat(order.getFundsReleasedAt()).isNotNull();
        ArgumentCaptor<PayoutDeltaRequest> delta = ArgumentCaptor.forClass(PayoutDeltaRequest.class);
        verify(userClient).applyPayoutDelta(eq(VENDOR), delta.capture());
        assertThat(delta.getValue().paidOutDelta()).isEqualByComparingTo("150.00");
        assertThat(delta.getValue().pendingDelta()).isEqualByComparingTo("-150.00");
    }

    @Test
    void vendorCannotConfirmReceiptOnBuyersBehalf() {
        OrderTransaction order = orderIn(EscrowStatus.SHIPPED);
        when(orderRepository.findByGuidForUpdate(order.getGuid())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.confirmReceipt(VENDOR, order.getGuid())).isInstanceOf(ApiException.class);
        assertThat(order.getEscrowStatus()).isEqualTo(EscrowStatus.SHIPPED);
    }

    @Test
    void cancelBeforePaymentRestoresStock() {
        OrderTransaction order = orderIn(EscrowStatus.AWAITING_PAYMENT);
        product.reserve(1);
        when(orderRepository.findByGuidForUpdate(order.getGuid())).thenReturn(Optional.of(order));
        when(productRepository.findByGuidForUpdate(product.getGuid())).thenReturn(Optional.of(product));

        service.cancel(BUYER, order.getGuid());

        assertThat(order.getEscrowStatus()).isEqualTo(EscrowStatus.CANCELLED);
        assertThat(product.getStockQuantity()).isEqualTo(2);
    }

    @Test
    void refundedDisputeReversesTheSale() {
        OrderTransaction order = orderIn(EscrowStatus.ESCROW_HELD);
        order.transitionTo(EscrowStatus.DISPUTED, null);
        when(orderRepository.findByGuidForUpdate(order.getGuid())).thenReturn(Optional.of(order));

        service.resolveDispute(order.getGuid(), ResolveDisputeRequest.Outcome.REFUND);

        assertThat(order.getEscrowStatus()).isEqualTo(EscrowStatus.REFUNDED);
        ArgumentCaptor<PayoutDeltaRequest> delta = ArgumentCaptor.forClass(PayoutDeltaRequest.class);
        verify(userClient).applyPayoutDelta(eq(VENDOR), delta.capture());
        assertThat(delta.getValue().salesCountDelta()).isEqualTo(-1);
        assertThat(delta.getValue().pendingDelta()).isEqualByComparingTo("-150.00");
    }

    private OrderTransaction orderIn(EscrowStatus status) {
        OrderTransaction order = new OrderTransaction(BUYER, product, 1);
        order.transitionTo(EscrowStatus.AWAITING_PAYMENT, null);
        if (status == EscrowStatus.AWAITING_PAYMENT) {
            return order;
        }
        order.transitionTo(EscrowStatus.ESCROW_HELD, null);
        if (status == EscrowStatus.SHIPPED) {
            order.transitionTo(EscrowStatus.SHIPPED, null);
        }
        return order;
    }
}

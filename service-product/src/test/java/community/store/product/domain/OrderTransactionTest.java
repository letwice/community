package community.store.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import community.store.common.web.ApiException;

class OrderTransactionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 12, 0);

    @Test
    void newOrderTotalsQuantityTimesUnitPrice() {
        OrderTransaction order = new OrderTransaction("buyer", product(), 3);

        assertThat(order.getTotalAmount()).isEqualByComparingTo("450.00");
        assertThat(order.getEscrowStatus()).isEqualTo(EscrowStatus.CREATED);
    }

    @Test
    void happyPathStampsEachMilestone() {
        OrderTransaction order = new OrderTransaction("buyer", product(), 1);

        order.transitionTo(EscrowStatus.AWAITING_PAYMENT, NOW);
        order.transitionTo(EscrowStatus.ESCROW_HELD, NOW.plusMinutes(1));
        order.transitionTo(EscrowStatus.SHIPPED, NOW.plusMinutes(2));
        order.transitionTo(EscrowStatus.DELIVERED_CONFIRMED, NOW.plusMinutes(3));
        order.transitionTo(EscrowStatus.FUNDS_RELEASED, NOW.plusMinutes(4));

        assertThat(order.getPaidAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(order.getShippedAt()).isEqualTo(NOW.plusMinutes(2));
        assertThat(order.getDeliveryConfirmedAt()).isEqualTo(NOW.plusMinutes(3));
        assertThat(order.getFundsReleasedAt()).isEqualTo(NOW.plusMinutes(4));
    }

    @Test
    void illegalTransitionIsRejectedWithConflict() {
        OrderTransaction order = new OrderTransaction("buyer", product(), 1);
        order.transitionTo(EscrowStatus.AWAITING_PAYMENT, NOW);

        assertThatThrownBy(() -> order.transitionTo(EscrowStatus.FUNDS_RELEASED, NOW))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(order.getEscrowStatus()).isEqualTo(EscrowStatus.AWAITING_PAYMENT);
    }

    private static Product product() {
        return new Product("vendor", "Textbook", "desc", new BigDecimal("150.00"), ProductCondition.SECOND_HAND, "Textbooks", 5);
    }
}

package community.store.product.domain;

import static community.store.product.domain.EscrowStatus.AWAITING_PAYMENT;
import static community.store.product.domain.EscrowStatus.CANCELLED;
import static community.store.product.domain.EscrowStatus.CREATED;
import static community.store.product.domain.EscrowStatus.DELIVERED_CONFIRMED;
import static community.store.product.domain.EscrowStatus.DISPUTED;
import static community.store.product.domain.EscrowStatus.ESCROW_HELD;
import static community.store.product.domain.EscrowStatus.FUNDS_RELEASED;
import static community.store.product.domain.EscrowStatus.REFUNDED;
import static community.store.product.domain.EscrowStatus.SHIPPED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class EscrowStatusTest {

    private static final Map<EscrowStatus, Set<EscrowStatus>> EXPECTED = Map.of(
            CREATED, EnumSet.of(AWAITING_PAYMENT),
            AWAITING_PAYMENT, EnumSet.of(ESCROW_HELD, CANCELLED),
            ESCROW_HELD, EnumSet.of(SHIPPED, DISPUTED),
            SHIPPED, EnumSet.of(DELIVERED_CONFIRMED, DISPUTED),
            DELIVERED_CONFIRMED, EnumSet.of(FUNDS_RELEASED),
            DISPUTED, EnumSet.of(REFUNDED, FUNDS_RELEASED),
            FUNDS_RELEASED, EnumSet.noneOf(EscrowStatus.class),
            CANCELLED, EnumSet.noneOf(EscrowStatus.class),
            REFUNDED, EnumSet.noneOf(EscrowStatus.class));

    @Test
    void everyTransitionMatchesTheDocumentedStateMachine() {
        for (EscrowStatus from : EscrowStatus.values()) {
            for (EscrowStatus to : EscrowStatus.values()) {
                assertThat(from.canTransitionTo(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(EXPECTED.get(from).contains(to));
            }
        }
    }

    @Test
    void vendorCannotShipBeforePaymentIsHeld() {
        assertThat(AWAITING_PAYMENT.canTransitionTo(SHIPPED)).isFalse();
    }

    @Test
    void terminalStatesHaveNoExits() {
        assertThat(FUNDS_RELEASED.isTerminal()).isTrue();
        assertThat(CANCELLED.isTerminal()).isTrue();
        assertThat(REFUNDED.isTerminal()).isTrue();
        assertThat(SHIPPED.isTerminal()).isFalse();
    }
}

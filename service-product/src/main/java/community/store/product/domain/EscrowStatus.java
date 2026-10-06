package community.store.product.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Simulated EFT escrow lifecycle. Allowed transitions mirror the state machine in the TA.
 */
public enum EscrowStatus {
    CREATED,
    AWAITING_PAYMENT,
    ESCROW_HELD,
    SHIPPED,
    DELIVERED_CONFIRMED,
    FUNDS_RELEASED,
    DISPUTED,
    CANCELLED,
    REFUNDED;

    private static final Map<EscrowStatus, Set<EscrowStatus>> TRANSITIONS = new EnumMap<>(EscrowStatus.class);

    static {
        TRANSITIONS.put(CREATED, EnumSet.of(AWAITING_PAYMENT));
        TRANSITIONS.put(AWAITING_PAYMENT, EnumSet.of(ESCROW_HELD, CANCELLED));
        TRANSITIONS.put(ESCROW_HELD, EnumSet.of(SHIPPED, DISPUTED));
        TRANSITIONS.put(SHIPPED, EnumSet.of(DELIVERED_CONFIRMED, DISPUTED));
        TRANSITIONS.put(DELIVERED_CONFIRMED, EnumSet.of(FUNDS_RELEASED));
        TRANSITIONS.put(DISPUTED, EnumSet.of(REFUNDED, FUNDS_RELEASED));
        TRANSITIONS.put(FUNDS_RELEASED, EnumSet.noneOf(EscrowStatus.class));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(EscrowStatus.class));
        TRANSITIONS.put(REFUNDED, EnumSet.noneOf(EscrowStatus.class));
    }

    public boolean canTransitionTo(EscrowStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    /** Orders an admin needs to act on: receipt confirmed (release) or disputed (release or refund). */
    public static Set<EscrowStatus> awaitingAdmin() {
        return EnumSet.of(DELIVERED_CONFIRMED, DISPUTED);
    }

    /** Statuses in which the buyer's money is (conceptually) held in escrow awaiting payout or refund. */
    public static Set<EscrowStatus> pendingPayout() {
        return EnumSet.of(ESCROW_HELD, SHIPPED, DELIVERED_CONFIRMED, DISPUTED);
    }
}

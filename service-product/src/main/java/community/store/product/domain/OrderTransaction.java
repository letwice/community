package community.store.product.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import community.store.common.web.ApiException;

/**
 * A sale. Status changes only go through {@link #transitionTo}, which enforces the escrow state machine.
 */
@Entity
@Table(name = "order_transaction")
public class OrderTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guid", nullable = false, unique = true, length = 36, updatable = false)
    private String guid;

    @Column(name = "buyer_user_guid", nullable = false, length = 36, updatable = false)
    private String buyerUserGuid;

    @Column(name = "vendor_user_guid", nullable = false, length = 36, updatable = false)
    private String vendorUserGuid;

    @Column(name = "product_guid", nullable = false, length = 36, updatable = false)
    private String productGuid;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "escrow_status", nullable = false)
    private EscrowStatus escrowStatus;

    @Column(name = "escrow_reference_number", length = 32)
    private String escrowReferenceNumber;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivery_confirmed_at")
    private LocalDateTime deliveryConfirmedAt;

    @Column(name = "funds_released_at")
    private LocalDateTime fundsReleasedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected OrderTransaction() {
    }

    public OrderTransaction(String buyerUserGuid, Product product, int quantity) {
        this.guid = UUID.randomUUID().toString();
        this.buyerUserGuid = buyerUserGuid;
        this.vendorUserGuid = product.getVendorUserGuid();
        this.productGuid = product.getGuid();
        this.quantity = quantity;
        this.unitPrice = product.getPrice();
        this.totalAmount = product.getPrice().multiply(BigDecimal.valueOf(quantity));
        this.escrowStatus = EscrowStatus.CREATED;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void transitionTo(EscrowStatus target, LocalDateTime now) {
        if (!escrowStatus.canTransitionTo(target)) {
            throw ApiException.conflict("Order " + displayReference() + " cannot move from " + escrowStatus + " to " + target);
        }
        escrowStatus = target;
        switch (target) {
            case ESCROW_HELD -> paidAt = now;
            case SHIPPED -> shippedAt = now;
            case DELIVERED_CONFIRMED -> deliveryConfirmedAt = now;
            case FUNDS_RELEASED -> fundsReleasedAt = now;
            default -> {
                // no timestamp for the remaining statuses
            }
        }
    }

    /** Human-readable reference standing in for the escrow account number, e.g. {@code ESC-000123}. */
    public void assignEscrowReference() {
        escrowReferenceNumber = String.format("ESC-%06d", id);
    }

    public boolean isBuyer(String userGuid) {
        return buyerUserGuid.equals(userGuid);
    }

    public boolean isVendor(String userGuid) {
        return vendorUserGuid.equals(userGuid);
    }

    private String displayReference() {
        return escrowReferenceNumber != null ? escrowReferenceNumber : guid;
    }

    public Long getId() {
        return id;
    }

    public String getGuid() {
        return guid;
    }

    public String getBuyerUserGuid() {
        return buyerUserGuid;
    }

    public String getVendorUserGuid() {
        return vendorUserGuid;
    }

    public String getProductGuid() {
        return productGuid;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public EscrowStatus getEscrowStatus() {
        return escrowStatus;
    }

    public String getEscrowReferenceNumber() {
        return escrowReferenceNumber;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public LocalDateTime getShippedAt() {
        return shippedAt;
    }

    public LocalDateTime getDeliveryConfirmedAt() {
        return deliveryConfirmedAt;
    }

    public LocalDateTime getFundsReleasedAt() {
        return fundsReleasedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

package community.store.product.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
        String guid,
        String buyerUserGuid,
        String vendorUserGuid,
        String productGuid,
        String productTitle,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        String escrowStatus,
        String escrowReferenceNumber,
        LocalDateTime paidAt,
        LocalDateTime shippedAt,
        LocalDateTime deliveryConfirmedAt,
        LocalDateTime fundsReleasedAt,
        LocalDateTime createdAt,
        boolean reviewed) {
}

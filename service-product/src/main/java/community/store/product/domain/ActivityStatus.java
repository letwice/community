package community.store.product.domain;

/**
 * PENDING_APPROVAL and REJECTED were added for admin moderation: new and edited events wait for an admin
 * before they appear on the bulletin.
 */
public enum ActivityStatus {
    DRAFT, PENDING_APPROVAL, PUBLISHED, REJECTED, CANCELLED, COMPLETED
}

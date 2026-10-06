package community.store.product.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_review")
public class ProductReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_guid", nullable = false, length = 36)
    private String productGuid;

    @Column(name = "reviewer_user_guid", nullable = false, length = 36)
    private String reviewerUserGuid;

    @Column(name = "order_transaction_guid", nullable = false, unique = true, length = 36)
    private String orderTransactionGuid;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ProductReview() {
    }

    public ProductReview(String productGuid, String reviewerUserGuid, String orderTransactionGuid, int rating, String comment) {
        this.productGuid = productGuid;
        this.reviewerUserGuid = reviewerUserGuid;
        this.orderTransactionGuid = orderTransactionGuid;
        this.rating = rating;
        this.comment = comment;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getProductGuid() {
        return productGuid;
    }

    public String getReviewerUserGuid() {
        return reviewerUserGuid;
    }

    public String getOrderTransactionGuid() {
        return orderTransactionGuid;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

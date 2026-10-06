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
@Table(name = "activity_review")
public class ActivityReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "community_activity_guid", nullable = false, length = 36)
    private String communityActivityGuid;

    @Column(name = "reviewer_user_guid", nullable = false, length = 36)
    private String reviewerUserGuid;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ActivityReview() {
    }

    public ActivityReview(String communityActivityGuid, String reviewerUserGuid, int rating, String comment) {
        this.communityActivityGuid = communityActivityGuid;
        this.reviewerUserGuid = reviewerUserGuid;
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

    public String getCommunityActivityGuid() {
        return communityActivityGuid;
    }

    public String getReviewerUserGuid() {
        return reviewerUserGuid;
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

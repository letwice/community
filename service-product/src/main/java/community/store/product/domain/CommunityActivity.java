package community.store.product.domain;

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

@Entity
@Table(name = "community_activity")
public class CommunityActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guid", nullable = false, unique = true, length = 36, updatable = false)
    private String guid;

    @Column(name = "organiser_user_guid", nullable = false, length = 36, updatable = false)
    private String organiserUserGuid;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "activity_date", nullable = false)
    private LocalDateTime activityDate;

    @Column(name = "location")
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ActivityStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected CommunityActivity() {
    }

    public CommunityActivity(String organiserUserGuid, String title, String description, LocalDateTime activityDate, String location) {
        this.guid = UUID.randomUUID().toString();
        this.organiserUserGuid = organiserUserGuid;
        this.title = title;
        this.description = description;
        this.activityDate = activityDate;
        this.location = location;
        this.status = ActivityStatus.PENDING_APPROVAL;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isOrganisedBy(String userGuid) {
        return organiserUserGuid.equals(userGuid);
    }

    public boolean hasTakenPlace(LocalDateTime now) {
        return activityDate.isBefore(now);
    }

    public void update(String title, String description, LocalDateTime activityDate, String location) {
        if (status == ActivityStatus.CANCELLED) {
            throw ApiException.conflict("Cancelled activities cannot be edited");
        }
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (activityDate != null) {
            this.activityDate = activityDate;
        }
        if (location != null) {
            this.location = location;
        }
    }

    public void cancel() {
        status = ActivityStatus.CANCELLED;
    }

    /** Edits to a published or rejected event go back to the admin approval queue. */
    public boolean resubmitIfNeeded() {
        if (status == ActivityStatus.PUBLISHED || status == ActivityStatus.REJECTED) {
            status = ActivityStatus.PENDING_APPROVAL;
            return true;
        }
        return false;
    }

    public void approve() {
        if (status != ActivityStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("Only events awaiting approval can be approved (status: " + status + ")");
        }
        status = ActivityStatus.PUBLISHED;
    }

    public void reject() {
        if (status != ActivityStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("Only events awaiting approval can be rejected (status: " + status + ")");
        }
        status = ActivityStatus.REJECTED;
    }

    /** Visible on the public bulletin (and reviewable once past). */
    public boolean isPublic() {
        return status == ActivityStatus.PUBLISHED || status == ActivityStatus.COMPLETED;
    }

    public Long getId() {
        return id;
    }

    public String getGuid() {
        return guid;
    }

    public String getOrganiserUserGuid() {
        return organiserUserGuid;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getActivityDate() {
        return activityDate;
    }

    public String getLocation() {
        return location;
    }

    public ActivityStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

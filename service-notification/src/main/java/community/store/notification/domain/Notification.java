package community.store.notification.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_guid", nullable = false, length = 36)
    private String userGuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    @Column(name = "read_flag", nullable = false)
    private boolean readFlag;

    @Enumerated(EnumType.STRING)
    @Column(name = "related_entity_type")
    private RelatedEntityType relatedEntityType;

    @Column(name = "related_entity_id", length = 36)
    private String relatedEntityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispatch_status", nullable = false)
    private DispatchStatus dispatchStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    protected Notification() {
    }

    public Notification(String userGuid, NotificationType type, String subject, String body,
                        RelatedEntityType relatedEntityType, String relatedEntityId, DispatchStatus dispatchStatus) {
        this.userGuid = userGuid;
        this.type = type;
        this.subject = subject;
        this.body = body;
        this.relatedEntityType = relatedEntityType;
        this.relatedEntityId = relatedEntityId;
        this.dispatchStatus = dispatchStatus;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void markRead() {
        if (!readFlag) {
            readFlag = true;
            readAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getUserGuid() {
        return userGuid;
    }

    public NotificationType getType() {
        return type;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public boolean isReadFlag() {
        return readFlag;
    }

    public RelatedEntityType getRelatedEntityType() {
        return relatedEntityType;
    }

    public String getRelatedEntityId() {
        return relatedEntityId;
    }

    public DispatchStatus getDispatchStatus() {
        return dispatchStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }
}

package community.store.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import community.store.common.client.dto.CreateNotificationRequest;
import community.store.common.web.ApiException;
import community.store.common.web.PageResponse;
import community.store.notification.domain.DispatchStatus;
import community.store.notification.domain.Notification;
import community.store.notification.domain.NotificationRepository;
import community.store.notification.domain.NotificationType;
import community.store.notification.domain.RelatedEntityType;
import community.store.notification.dto.NotificationResponse;
import community.store.notification.dto.UnreadCountResponse;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * EMAIL/SMS are never dispatched in the PoC: the payload is logged and marked SIMULATED_SENT.
     * RICH_INBOX notifications are delivered straight to the user's inbox.
     */
    @Transactional
    public NotificationResponse create(CreateNotificationRequest request) {
        NotificationType type = parse(NotificationType.class, request.type(), "type");
        RelatedEntityType relatedType = request.relatedEntityType() == null
                ? null : parse(RelatedEntityType.class, request.relatedEntityType(), "relatedEntityType");
        DispatchStatus dispatchStatus;
        if (type == NotificationType.RICH_INBOX) {
            dispatchStatus = DispatchStatus.DELIVERED_INBOX;
        } else {
            dispatchStatus = DispatchStatus.SIMULATED_SENT;
            log.info("[SIMULATED {}] to user {} | subject: {} | body: {}", type, request.userGuid(), request.subject(), request.body());
        }
        Notification saved = repository.save(new Notification(request.userGuid(), type, request.subject(), request.body(),
                relatedType, request.relatedEntityId(), dispatchStatus));
        log.debug("Created {} notification {} for user {}", type, saved.getId(), request.userGuid());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> inbox(String userGuid, NotificationType type, Boolean read, Pageable pageable) {
        return PageResponse.from(repository.findInbox(userGuid, type, read, pageable), this::toResponse);
    }

    @Transactional
    public NotificationResponse markRead(String userGuid, Long id) {
        Notification notification = repository.findByIdAndUserGuid(id, userGuid)
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        notification.markRead();
        return toResponse(notification);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(String userGuid) {
        return new UnreadCountResponse(repository.countByUserGuidAndTypeAndReadFlagFalse(userGuid, NotificationType.RICH_INBOX));
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getUserGuid(), n.getType().name(), n.getSubject(), n.getBody(),
                n.isReadFlag(), n.getRelatedEntityType() == null ? null : n.getRelatedEntityType().name(),
                n.getRelatedEntityId(), n.getDispatchStatus().name(), n.getCreatedAt(), n.getReadAt());
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw ApiException.badRequest("Unsupported " + field + ": " + value);
        }
    }
}

package community.store.common.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import community.store.common.client.dto.CreateNotificationRequest;

/**
 * Client for {@code service-notification}. Notifications are best-effort: a failure is logged and
 * never rolls back the business operation that triggered it.
 */
public class NotificationClient {

    public static final String TYPE_EMAIL = "EMAIL";
    public static final String TYPE_SMS = "SMS";
    public static final String TYPE_RICH_INBOX = "RICH_INBOX";

    public static final String ENTITY_ORDER = "ORDER";
    public static final String ENTITY_PRODUCT = "PRODUCT";
    public static final String ENTITY_ACTIVITY = "ACTIVITY";
    public static final String ENTITY_ACCOUNT = "ACCOUNT";

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    private final RestClient restClient;

    public NotificationClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public void inbox(String userGuid, String subject, String body, String relatedEntityType, String relatedEntityId) {
        send(new CreateNotificationRequest(userGuid, TYPE_RICH_INBOX, subject, body, relatedEntityType, relatedEntityId));
    }

    public void email(String userGuid, String subject, String body, String relatedEntityType, String relatedEntityId) {
        send(new CreateNotificationRequest(userGuid, TYPE_EMAIL, subject, body, relatedEntityType, relatedEntityId));
    }

    public void send(CreateNotificationRequest request) {
        try {
            restClient.post().uri("/system/notifications").body(request).retrieve().toBodilessEntity();
        } catch (RuntimeException ex) {
            log.warn("Failed to create {} notification for user {}: {}", request.type(), request.userGuid(), ex.getMessage());
        }
    }
}

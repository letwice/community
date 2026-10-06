package community.store.common.client;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends a rich-inbox notification to every active admin. Best-effort: failures are logged and never roll back
 * the business operation that raised the alert.
 */
public class AdminNotifier {

    private static final Logger log = LoggerFactory.getLogger(AdminNotifier.class);

    private final UserClient userClient;
    private final NotificationClient notificationClient;

    public AdminNotifier(UserClient userClient, NotificationClient notificationClient) {
        this.userClient = userClient;
        this.notificationClient = notificationClient;
    }

    public void notifyAdmins(String subject, String body, String relatedEntityType, String relatedEntityId) {
        List<String> admins;
        try {
            admins = userClient.adminGuids();
        } catch (RuntimeException ex) {
            log.warn("Could not look up admins for alert '{}': {}", subject, ex.getMessage());
            return;
        }
        admins.forEach(admin -> notificationClient.inbox(admin, subject, body, relatedEntityType, relatedEntityId));
        log.info("Admin alert '{}' sent to {} admin(s)", subject, admins.size());
    }
}

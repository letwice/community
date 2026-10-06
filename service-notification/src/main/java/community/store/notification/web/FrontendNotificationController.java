package community.store.notification.web;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import community.store.common.security.CurrentUser;
import community.store.common.web.PageResponse;
import community.store.notification.domain.NotificationType;
import community.store.notification.dto.NotificationResponse;
import community.store.notification.dto.UnreadCountResponse;
import community.store.notification.service.NotificationService;

@RestController
@RequestMapping("/frontend/notifications")
@PreAuthorize("isAuthenticated()")
public class FrontendNotificationController {

    private final NotificationService notificationService;

    public FrontendNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * The inbox shows RICH_INBOX notifications by default; pass {@code type=EMAIL|SMS} to see the simulated outbox.
     */
    @GetMapping
    public PageResponse<NotificationResponse> list(@RequestParam(required = false) Boolean read,
                                                   @RequestParam(defaultValue = "RICH_INBOX") NotificationType type,
                                                   @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return notificationService.inbox(CurrentUser.guid(), type, read, pageable);
    }

    @PutMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id) {
        return notificationService.markRead(CurrentUser.guid(), id);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount() {
        return notificationService.unreadCount(CurrentUser.guid());
    }
}

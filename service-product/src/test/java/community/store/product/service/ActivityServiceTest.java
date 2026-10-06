package community.store.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import community.store.common.client.AdminNotifier;
import community.store.common.client.NotificationClient;
import community.store.common.client.UserClient;
import community.store.common.web.ApiException;
import community.store.product.domain.ActivityReviewRepository;
import community.store.product.domain.ActivityStatus;
import community.store.product.domain.CommunityActivity;
import community.store.product.domain.CommunityActivityRepository;
import community.store.product.dto.ActivityResponse;
import community.store.product.dto.CreateActivityRequest;
import community.store.product.dto.CreateActivityReviewRequest;
import community.store.product.dto.ReviewResponse;
import community.store.product.dto.UpdateActivityRequest;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 10, 0);

    @Mock
    private CommunityActivityRepository activityRepository;
    @Mock
    private ActivityReviewRepository reviewRepository;
    @Mock
    private AccountChecks accountChecks;
    @Mock
    private NotificationClient notificationClient;
    @Mock
    private AdminNotifier adminNotifier;
    @Mock
    private RatingWatch ratingWatch;
    @Mock
    private UserClient userClient;

    private ActivityService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new ActivityService(activityRepository, reviewRepository, accountChecks, notificationClient, adminNotifier,
                ratingWatch, userClient, clock);
    }

    @Test
    void cannotReviewActivityBeforeItTakesPlace() {
        CommunityActivity upcoming = published(NOW.plusDays(1));
        when(activityRepository.findByGuid(upcoming.getGuid())).thenReturn(Optional.of(upcoming));

        assertThatThrownBy(() -> service.review("student", upcoming.getGuid(), new CreateActivityReviewRequest(5, "Great")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("once it has taken place");
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void canReviewPastActivityOnce() {
        CommunityActivity past = published(NOW.minusDays(1));
        when(activityRepository.findByGuid(past.getGuid())).thenReturn(Optional.of(past));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse review = service.review("student", past.getGuid(), new CreateActivityReviewRequest(4, "Good"));

        assertThat(review.rating()).isEqualTo(4);
        verify(ratingWatch).afterReview(eq("leader"), eq("Event organiser"), any(), eq(4));
    }

    @Test
    void newEventWaitsForApprovalAndAlertsAdmins() {
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ActivityResponse created = service.create("leader", new CreateActivityRequest("Talk", null, NOW.plusDays(2), "Hall"));

        assertThat(created.status()).isEqualTo("PENDING_APPROVAL");
        verify(adminNotifier).notifyAdmins(eq("New event awaiting approval"), anyString(), eq(NotificationClient.ENTITY_ACTIVITY), eq(created.guid()));
    }

    @Test
    void approvingPublishesAndNotifiesOrganiser() {
        CommunityActivity pending = new CommunityActivity("leader", "Talk", null, NOW.plusDays(2), "Hall");
        when(activityRepository.findByGuid(pending.getGuid())).thenReturn(Optional.of(pending));

        assertThat(service.approve(pending.getGuid()).status()).isEqualTo("PUBLISHED");
        verify(notificationClient).inbox(eq("leader"), eq("Event approved"), anyString(), anyString(), anyString());
        assertThatThrownBy(() -> service.approve(pending.getGuid())).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectingNotifiesOrganiserWithReasonAndEditingResubmits() {
        CommunityActivity pending = new CommunityActivity("leader", "Talk", null, NOW.plusDays(2), "Hall");
        when(activityRepository.findByGuid(pending.getGuid())).thenReturn(Optional.of(pending));

        service.reject(pending.getGuid(), "Missing venue booking.");
        assertThat(pending.getStatus()).isEqualTo(ActivityStatus.REJECTED);
        verify(notificationClient).inbox(eq("leader"), eq("Event not approved"), contains("Missing venue booking."), anyString(), anyString());

        service.update("leader", pending.getGuid(), new UpdateActivityRequest(null, null, null, "Hall B"));
        assertThat(pending.getStatus()).isEqualTo(ActivityStatus.PENDING_APPROVAL);
        verify(adminNotifier).notifyAdmins(eq("Edited event awaiting approval"), anyString(), anyString(), anyString());
    }

    @Test
    void pendingEventIsHiddenFromOtherUsers() {
        CommunityActivity pending = new CommunityActivity("leader", "Talk", null, NOW.plusDays(2), "Hall");
        when(activityRepository.findByGuid(pending.getGuid())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.detail(pending.getGuid(), "student", false)).isInstanceOf(ApiException.class);
    }

    @Test
    void unapprovedPastEventCannotBeReviewed() {
        CommunityActivity pending = new CommunityActivity("leader", "Talk", null, NOW.minusDays(1), "Hall");
        when(activityRepository.findByGuid(pending.getGuid())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.review("student", pending.getGuid(), new CreateActivityReviewRequest(5, null)))
                .isInstanceOf(ApiException.class);
    }

    private static CommunityActivity published(LocalDateTime when) {
        CommunityActivity activity = new CommunityActivity("leader", "Talk", null, when, "Hall");
        activity.approve();
        return activity;
    }

    @Test
    void organiserCannotReviewOwnActivity() {
        CommunityActivity past = published(NOW.minusDays(1));
        when(activityRepository.findByGuid(past.getGuid())).thenReturn(Optional.of(past));

        assertThatThrownBy(() -> service.review("leader", past.getGuid(), new CreateActivityReviewRequest(5, null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void unverifiedOrganiserCannotPublish() {
        when(accountChecks.requireVerifiedOrganiser("leader")).thenThrow(ApiException.forbidden("not verified"));

        assertThatThrownBy(() -> service.create("leader", new CreateActivityRequest("Talk", null, NOW.plusDays(2), "Hall")))
                .isInstanceOf(ApiException.class);
        verify(activityRepository, never()).save(any());
    }
}

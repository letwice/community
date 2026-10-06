package community.store.product.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import community.store.common.client.AdminNotifier;
import community.store.common.client.NotificationClient;
import community.store.common.client.UserClient;
import community.store.common.client.dto.UserSummaryResponse;
import community.store.common.web.ApiException;
import community.store.common.web.PageResponse;
import community.store.product.domain.ActivityReview;
import community.store.product.domain.ActivityReviewRepository;
import community.store.product.domain.ActivityStatus;
import community.store.product.domain.CommunityActivity;
import community.store.product.domain.CommunityActivityRepository;
import community.store.product.domain.GuidRatingStats;
import community.store.product.dto.ActivityDetailResponse;
import community.store.product.dto.AdminActivityResponse;
import community.store.product.dto.AdminReviewResponse;
import community.store.product.dto.ActivityResponse;
import community.store.product.dto.CreateActivityRequest;
import community.store.product.dto.CreateActivityReviewRequest;
import community.store.product.dto.ReviewResponse;
import community.store.product.dto.UpdateActivityRequest;

@Service
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);
    private static final EnumSet<ActivityStatus> VISIBLE = EnumSet.of(ActivityStatus.PUBLISHED, ActivityStatus.COMPLETED);
    private static final int DETAIL_REVIEW_COUNT = 10;

    private final CommunityActivityRepository activityRepository;
    private final ActivityReviewRepository reviewRepository;
    private final AccountChecks accountChecks;
    private final NotificationClient notificationClient;
    private final AdminNotifier adminNotifier;
    private final RatingWatch ratingWatch;
    private final UserClient userClient;
    private final Clock clock;

    public ActivityService(CommunityActivityRepository activityRepository,
                           ActivityReviewRepository reviewRepository,
                           AccountChecks accountChecks,
                           NotificationClient notificationClient,
                           AdminNotifier adminNotifier,
                           RatingWatch ratingWatch,
                           UserClient userClient,
                           Clock clock) {
        this.activityRepository = activityRepository;
        this.reviewRepository = reviewRepository;
        this.accountChecks = accountChecks;
        this.notificationClient = notificationClient;
        this.adminNotifier = adminNotifier;
        this.ratingWatch = ratingWatch;
        this.userClient = userClient;
        this.clock = clock;
    }

    @Transactional
    public ActivityResponse create(String organiserGuid, CreateActivityRequest request) {
        accountChecks.requireVerifiedOrganiser(organiserGuid);
        CommunityActivity activity = activityRepository.save(new CommunityActivity(organiserGuid, request.title(),
                request.description(), request.activityDate(), request.location()));
        log.info("User {} submitted activity {} ({}) for approval", organiserGuid, activity.getGuid(), activity.getTitle());
        requestApproval(activity, "New event awaiting approval");
        return toResponse(activity);
    }

    /** Editing a published or rejected event sends it back for admin approval. */
    @Transactional
    public ActivityResponse update(String organiserGuid, String activityGuid, UpdateActivityRequest request) {
        CommunityActivity activity = loadOwned(organiserGuid, activityGuid);
        activity.update(request.title(), request.description(), request.activityDate(), request.location());
        if (activity.resubmitIfNeeded()) {
            requestApproval(activity, "Edited event awaiting approval");
        }
        return toResponse(activity);
    }

    @Transactional
    public ActivityResponse approve(String activityGuid) {
        CommunityActivity activity = load(activityGuid);
        activity.approve();
        log.info("Activity {} approved", activityGuid);
        notificationClient.inbox(activity.getOrganiserUserGuid(), "Event approved",
                "\"" + activity.getTitle() + "\" is now live on the campus bulletin.", NotificationClient.ENTITY_ACTIVITY, activityGuid);
        return toResponse(activity);
    }

    @Transactional
    public ActivityResponse reject(String activityGuid, String reason) {
        CommunityActivity activity = load(activityGuid);
        activity.reject();
        log.info("Activity {} rejected: {}", activityGuid, reason);
        notificationClient.inbox(activity.getOrganiserUserGuid(), "Event not approved",
                "\"" + activity.getTitle() + "\" was not approved. Reason: " + reason + " You can edit and resubmit it.",
                NotificationClient.ENTITY_ACTIVITY, activityGuid);
        return toResponse(activity);
    }

    /** Admin overview of every event (any status) with organiser names and ratings. */
    @Transactional(readOnly = true)
    public PageResponse<AdminActivityResponse> adminList(ActivityStatus status, Pageable pageable) {
        Pageable sorted = withSort(pageable, Sort.by(Sort.Direction.DESC, "activityDate"));
        Page<CommunityActivity> page = status == null ? activityRepository.findAll(sorted)
                : activityRepository.findByStatusIn(EnumSet.of(status), sorted);
        Set<String> guids = page.map(CommunityActivity::getGuid).toSet();
        Map<String, GuidRatingStats> ratings = guids.isEmpty() ? Map.of() : reviewRepository.statsForActivities(guids).stream()
                .collect(Collectors.toMap(GuidRatingStats::getGuid, Function.identity()));
        Map<String, UserSummaryResponse> organisers = userClient.getUsers(page.map(CommunityActivity::getOrganiserUserGuid).toSet());
        return PageResponse.from(page, a -> {
            GuidRatingStats rating = ratings.get(a.getGuid());
            return new AdminActivityResponse(toResponse(a), ReviewService.nameOf(organisers.get(a.getOrganiserUserGuid())),
                    rating == null ? null : rating.average(), rating == null ? 0 : rating.getReviewCount());
        });
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminReviewResponse> adminReviews(String activityGuid, Pageable pageable) {
        CommunityActivity activity = load(activityGuid);
        Page<ActivityReview> page = reviewRepository.findByCommunityActivityGuid(activityGuid, pageable);
        Map<String, UserSummaryResponse> reviewers = userClient.getUsers(page.map(ActivityReview::getReviewerUserGuid).toSet());
        return PageResponse.from(page, r -> new AdminReviewResponse(r.getId(), activityGuid, activity.getTitle(),
                r.getReviewerUserGuid(), ReviewService.nameOf(reviewers.get(r.getReviewerUserGuid())), r.getRating(),
                r.getComment(), r.getCreatedAt()));
    }

    private void requestApproval(CommunityActivity activity, String subject) {
        adminNotifier.notifyAdmins(subject, "\"" + activity.getTitle() + "\" on " + activity.getActivityDate().toLocalDate()
                + " needs approval before it appears on the bulletin.", NotificationClient.ENTITY_ACTIVITY, activity.getGuid());
    }

    @Transactional
    public void cancel(String organiserGuid, String activityGuid) {
        loadOwned(organiserGuid, activityGuid).cancel();
        log.info("Activity {} cancelled by {}", activityGuid, organiserGuid);
    }

    /**
     * {@code upcoming=true}: published activities still to come (soonest first); {@code false}: past activities
     * (most recent first); null: everything visible.
     */
    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> list(Boolean upcoming, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now(clock);
        Page<CommunityActivity> page;
        if (Boolean.TRUE.equals(upcoming)) {
            page = activityRepository.findByStatusAndActivityDateAfter(ActivityStatus.PUBLISHED, now,
                    withSort(pageable, Sort.by(Sort.Direction.ASC, "activityDate")));
        } else if (Boolean.FALSE.equals(upcoming)) {
            page = activityRepository.findByStatusInAndActivityDateBefore(VISIBLE, now,
                    withSort(pageable, Sort.by(Sort.Direction.DESC, "activityDate")));
        } else {
            page = activityRepository.findByStatusIn(VISIBLE, withSort(pageable, Sort.by(Sort.Direction.DESC, "activityDate")));
        }
        return PageResponse.from(page, ActivityService::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> listMine(String organiserGuid, Pageable pageable) {
        return PageResponse.from(activityRepository.findByOrganiserUserGuid(organiserGuid,
                withSort(pageable, Sort.by(Sort.Direction.DESC, "activityDate"))), ActivityService::toResponse);
    }

    /** Unapproved events are only visible to their organiser and admins. */
    @Transactional(readOnly = true)
    public ActivityDetailResponse detail(String activityGuid, String viewerGuid, boolean admin) {
        CommunityActivity activity = load(activityGuid);
        if (!activity.isPublic() && !admin && !activity.isOrganisedBy(viewerGuid)
                && activity.getStatus() != ActivityStatus.CANCELLED) {
            throw ApiException.notFound("Activity not found");
        }
        return new ActivityDetailResponse(toResponse(activity),
                reviewRepository.averageRating(activityGuid),
                reviewRepository.countByCommunityActivityGuid(activityGuid),
                listReviews(activityGuid, PageRequest.of(0, DETAIL_REVIEW_COUNT, Sort.by(Sort.Direction.DESC, "createdAt"))).content());
    }

    /**
     * Reviews are only accepted once the activity date has passed, once per reviewer, and never by the organiser.
     */
    @Transactional
    public ReviewResponse review(String reviewerGuid, String activityGuid, CreateActivityReviewRequest request) {
        CommunityActivity activity = load(activityGuid);
        if (!activity.isPublic()) {
            throw ApiException.conflict("This activity cannot be reviewed");
        }
        if (!activity.hasTakenPlace(LocalDateTime.now(clock))) {
            throw ApiException.conflict("You can review this activity once it has taken place");
        }
        if (activity.isOrganisedBy(reviewerGuid)) {
            throw ApiException.forbidden("Organisers cannot review their own activity");
        }
        if (reviewRepository.existsByCommunityActivityGuidAndReviewerUserGuid(activityGuid, reviewerGuid)) {
            throw ApiException.conflict("You have already reviewed this activity");
        }
        ActivityReview review = reviewRepository.save(new ActivityReview(activityGuid, reviewerGuid, request.rating(), request.comment()));
        log.info("User {} reviewed activity {} ({} stars)", reviewerGuid, activityGuid, request.rating());
        ratingWatch.afterReview(activity.getOrganiserUserGuid(), "Event organiser",
                reviewRepository.statsForOrganiser(activity.getOrganiserUserGuid()), request.rating());
        return toResponse(review);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listReviews(String activityGuid, Pageable pageable) {
        return PageResponse.from(reviewRepository.findByCommunityActivityGuid(activityGuid, pageable), ActivityService::toResponse);
    }

    private CommunityActivity load(String activityGuid) {
        return activityRepository.findByGuid(activityGuid).orElseThrow(() -> ApiException.notFound("Activity not found"));
    }

    private CommunityActivity loadOwned(String organiserGuid, String activityGuid) {
        CommunityActivity activity = load(activityGuid);
        if (!activity.isOrganisedBy(organiserGuid)) {
            throw ApiException.forbidden("You can only manage your own activities");
        }
        return activity;
    }

    private static Pageable withSort(Pageable pageable, Sort defaultSort) {
        return pageable.getSort().isSorted() ? pageable : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
    }

    private static ActivityResponse toResponse(CommunityActivity a) {
        return new ActivityResponse(a.getGuid(), a.getOrganiserUserGuid(), a.getTitle(), a.getDescription(),
                a.getActivityDate(), a.getLocation(), a.getStatus().name(), a.getCreatedAt());
    }

    private static ReviewResponse toResponse(ActivityReview r) {
        return new ReviewResponse(r.getId(), r.getCommunityActivityGuid(), r.getReviewerUserGuid(), r.getRating(), r.getComment(), r.getCreatedAt());
    }
}

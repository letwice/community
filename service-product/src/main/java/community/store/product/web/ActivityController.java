package community.store.product.web;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import community.store.common.security.CurrentUser;
import community.store.common.security.Roles;
import community.store.common.web.PageResponse;
import community.store.product.dto.ActivityDetailResponse;
import community.store.product.dto.ActivityResponse;
import community.store.product.dto.CreateActivityRequest;
import community.store.product.dto.CreateActivityReviewRequest;
import community.store.product.dto.ReviewResponse;
import community.store.product.dto.UpdateActivityRequest;
import community.store.product.service.ActivityService;

@RestController
@RequestMapping("/frontend/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('COMMUNITY_LEADER', 'FACULTY')")
    public ActivityResponse create(@Valid @RequestBody CreateActivityRequest request) {
        return activityService.create(CurrentUser.guid(), request);
    }

    @PutMapping("/{guid}")
    @PreAuthorize("hasAnyRole('COMMUNITY_LEADER', 'FACULTY')")
    public ActivityResponse update(@PathVariable String guid, @Valid @RequestBody UpdateActivityRequest request) {
        return activityService.update(CurrentUser.guid(), guid, request);
    }

    @DeleteMapping("/{guid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('COMMUNITY_LEADER', 'FACULTY')")
    public void cancel(@PathVariable String guid) {
        activityService.cancel(CurrentUser.guid(), guid);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<ActivityResponse> list(@RequestParam(required = false) Boolean upcoming,
                                               @RequestParam(defaultValue = "false") boolean mine,
                                               @PageableDefault(size = 20) Pageable pageable) {
        if (mine) {
            return activityService.listMine(CurrentUser.guid(), pageable);
        }
        return activityService.list(upcoming, pageable);
    }

    @GetMapping("/{guid}")
    @PreAuthorize("isAuthenticated()")
    public ActivityDetailResponse detail(@PathVariable String guid) {
        return activityService.detail(guid, CurrentUser.guid(), CurrentUser.hasRole(Roles.ADMIN));
    }

    @PostMapping("/{guid}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('STUDENT', 'VENDOR', 'COMMUNITY_LEADER', 'FACULTY')")
    public ReviewResponse review(@PathVariable String guid, @Valid @RequestBody CreateActivityReviewRequest request) {
        return activityService.review(CurrentUser.guid(), guid, request);
    }

    @GetMapping("/{guid}/reviews")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<ReviewResponse> reviews(@PathVariable String guid,
                                                @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return activityService.listReviews(guid, pageable);
    }
}

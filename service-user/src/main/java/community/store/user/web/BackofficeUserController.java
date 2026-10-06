package community.store.user.web;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import community.store.common.security.CurrentUser;
import community.store.common.web.PageResponse;
import community.store.user.domain.Role;
import community.store.user.domain.UserStatus;
import community.store.user.domain.VerificationStatus;
import community.store.user.dto.AdminUserDetailResponse;
import community.store.user.dto.UpdateUserRolesRequest;
import community.store.user.dto.UpdateUserStatusRequest;
import community.store.user.dto.RejectUserRequest;
import community.store.user.dto.UserResponse;
import community.store.user.service.AccountService;
import community.store.user.service.UserService;

@RestController
@RequestMapping("/backoffice/users")
@PreAuthorize("hasRole('ADMIN')")
public class BackofficeUserController {

    private final UserService userService;
    private final AccountService accountService;

    public BackofficeUserController(UserService userService, AccountService accountService) {
        this.userService = userService;
        this.accountService = accountService;
    }

    /**
     * {@code status} filters on verification status (e.g. {@code PENDING_REVIEW} for the verification queue).
     */
    /**
     * {@code status} filters on verification status (e.g. {@code PENDING_REVIEW} for the verification queue),
     * {@code accountStatus} on ACTIVE/SUSPENDED/..., {@code q} is a starts-with match on email, first or last name.
     */
    @GetMapping
    public PageResponse<UserResponse> search(@RequestParam(required = false) VerificationStatus status,
                                             @RequestParam(required = false) UserStatus accountStatus,
                                             @RequestParam(required = false) Role role,
                                             @RequestParam(required = false) String q,
                                             @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return userService.search(status, accountStatus, role, q, pageable);
    }

    @GetMapping("/{guid}")
    public AdminUserDetailResponse detail(@PathVariable String guid) {
        return accountService.adminDetail(guid);
    }

    @PutMapping("/{guid}/status")
    public UserResponse changeStatus(@PathVariable String guid, @Valid @RequestBody UpdateUserStatusRequest request) {
        return userService.changeStatus(CurrentUser.guid(), guid, request.status(), request.reason());
    }

    @PutMapping("/{guid}/roles")
    public UserResponse updateRoles(@PathVariable String guid, @Valid @RequestBody UpdateUserRolesRequest request) {
        return userService.updateRoles(CurrentUser.guid(), guid, request.roles());
    }

    @PostMapping("/{guid}/verify")
    public UserResponse verify(@PathVariable String guid) {
        return userService.verify(guid);
    }

    @PostMapping("/{guid}/reject")
    public UserResponse reject(@PathVariable String guid, @Valid @RequestBody RejectUserRequest request) {
        return userService.reject(guid, request.reason());
    }
}

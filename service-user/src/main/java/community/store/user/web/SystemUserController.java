package community.store.user.web;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import community.store.common.client.dto.CreateUserRequest;
import community.store.common.client.dto.PayoutDeltaRequest;
import community.store.common.client.dto.RecordSessionRequest;
import community.store.common.client.dto.UserSummaryResponse;
import community.store.user.dto.PayoutSummaryResponse;
import community.store.user.service.AccountService;
import community.store.user.service.UserService;

/**
 * Internal service-to-service API; never exposed through a public gateway.
 */
@RestController
@RequestMapping("/system")
@PreAuthorize("hasRole('SYSTEM')")
public class SystemUserController {

    private final UserService userService;
    private final AccountService accountService;

    public SystemUserController(UserService userService, AccountService accountService) {
        this.userService = userService;
        this.accountService = accountService;
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserSummaryResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    /** Batch lookup, e.g. {@code /system/users?guid=a&guid=b}. Unknown guids are skipped. */
    @GetMapping("/users")
    public List<UserSummaryResponse> batch(@RequestParam(name = "guid", required = false) List<String> guids) {
        return userService.getSummaries(guids == null ? List.of() : guids);
    }

    @GetMapping("/users/admins")
    public List<String> admins() {
        return userService.activeAdminGuids();
    }

    @GetMapping("/users/by-email")
    public UserSummaryResponse byEmail(@RequestParam String email) {
        return userService.getSummaryByEmail(email);
    }

    @GetMapping("/users/{guid}")
    public UserSummaryResponse get(@PathVariable String guid) {
        return userService.getSummary(guid);
    }

    @PostMapping("/users/{guid}/email-verified")
    public UserSummaryResponse emailVerified(@PathVariable String guid) {
        return userService.markEmailVerified(guid);
    }

    @PostMapping("/users/{guid}/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordSession(@PathVariable String guid, @Valid @RequestBody RecordSessionRequest request) {
        accountService.recordSession(guid, request);
    }

    @PutMapping("/users/{guid}/payout-summary")
    public PayoutSummaryResponse applyPayoutDelta(@PathVariable String guid, @RequestBody PayoutDeltaRequest request) {
        return accountService.applyPayoutDelta(guid, request);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}

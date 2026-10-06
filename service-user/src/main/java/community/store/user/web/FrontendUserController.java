package community.store.user.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import community.store.common.security.CurrentUser;
import community.store.user.dto.BankingDetailRequest;
import community.store.user.dto.BankingDetailResponse;
import community.store.user.dto.PayoutSummaryResponse;
import community.store.user.dto.UpdateUserRequest;
import community.store.user.dto.UserResponse;
import community.store.user.service.AccountService;
import community.store.user.service.UserService;

@RestController
@RequestMapping("/frontend/users/me")
public class FrontendUserController {

    private final UserService userService;
    private final AccountService accountService;

    public FrontendUserController(UserService userService, AccountService accountService) {
        this.userService = userService;
        this.accountService = accountService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public UserResponse me() {
        return userService.getMe(CurrentUser.guid());
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public UserResponse update(@Valid @RequestBody UpdateUserRequest request) {
        return userService.updateMe(CurrentUser.guid(), request);
    }

    @PostMapping("/banking-details")
    @PreAuthorize("hasAnyRole('VENDOR', 'FACULTY')")
    public BankingDetailResponse saveBankingDetails(@Valid @RequestBody BankingDetailRequest request) {
        return accountService.saveBankingDetails(CurrentUser.guid(), request);
    }

    @GetMapping("/banking-details")
    @PreAuthorize("hasAnyRole('VENDOR', 'FACULTY')")
    public BankingDetailResponse bankingDetails() {
        return accountService.getBankingDetails(CurrentUser.guid());
    }

    @GetMapping("/payout-summary")
    @PreAuthorize("hasAnyRole('VENDOR', 'FACULTY')")
    public PayoutSummaryResponse payoutSummary() {
        return accountService.getPayoutSummary(CurrentUser.guid());
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        accountService.logout(CurrentUser.guid(), CurrentUser.tokenId());
    }
}

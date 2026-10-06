package community.store.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import community.store.common.client.dto.PayoutDeltaRequest;
import community.store.common.client.dto.RecordSessionRequest;
import community.store.common.web.ApiException;
import community.store.user.domain.UserBankingDetail;
import community.store.user.domain.UserBankingDetailRepository;
import community.store.user.domain.UserPayoutSummary;
import community.store.user.domain.UserPayoutSummaryRepository;
import community.store.user.domain.UserSessionActivity;
import community.store.user.domain.UserSessionActivityRepository;
import community.store.user.dto.AdminUserDetailResponse;
import community.store.user.dto.BankingDetailRequest;
import community.store.user.dto.BankingDetailResponse;
import community.store.user.dto.PayoutSummaryResponse;
import community.store.user.dto.UserResponse;

/**
 * Banking details, payout rollups and session activity for a user.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserService userService;
    private final UserBankingDetailRepository bankingDetailRepository;
    private final UserPayoutSummaryRepository payoutSummaryRepository;
    private final UserSessionActivityRepository sessionActivityRepository;

    public AccountService(UserService userService,
                          UserBankingDetailRepository bankingDetailRepository,
                          UserPayoutSummaryRepository payoutSummaryRepository,
                          UserSessionActivityRepository sessionActivityRepository) {
        this.userService = userService;
        this.bankingDetailRepository = bankingDetailRepository;
        this.payoutSummaryRepository = payoutSummaryRepository;
        this.sessionActivityRepository = sessionActivityRepository;
    }

    @Transactional
    public BankingDetailResponse saveBankingDetails(String guid, BankingDetailRequest request) {
        userService.load(guid);
        UserBankingDetail detail = bankingDetailRepository.findByUserGuid(guid).orElseGet(() -> new UserBankingDetail(guid));
        detail.update(request.accountHolderName(), request.bankName(), request.accountNumber(), request.branchCode(), request.accountType());
        bankingDetailRepository.save(detail);
        log.info("Banking details saved for user {}", guid);
        return toResponse(detail);
    }

    @Transactional(readOnly = true)
    public BankingDetailResponse getBankingDetails(String guid) {
        return bankingDetailRepository.findByUserGuid(guid).map(this::toResponse)
                .orElseThrow(() -> ApiException.notFound("No banking details on file"));
    }

    @Transactional(readOnly = true)
    public PayoutSummaryResponse getPayoutSummary(String guid) {
        return payoutSummaryRepository.findByUserGuid(guid)
                .map(this::toResponse)
                .orElseGet(() -> toResponse(new UserPayoutSummary(guid)));
    }

    @Transactional
    public PayoutSummaryResponse applyPayoutDelta(String guid, PayoutDeltaRequest delta) {
        userService.load(guid);
        UserPayoutSummary summary = payoutSummaryRepository.findByUserGuidForUpdate(guid)
                .orElseGet(() -> payoutSummaryRepository.save(new UserPayoutSummary(guid)));
        summary.apply(delta.salesCountDelta(), delta.salesAmountDelta(), delta.paidOutDelta(), delta.pendingDelta(), delta.payoutAt());
        log.info("Payout summary for {} updated: sales={}, paidOut={}, pending={}", guid,
                summary.getTotalSalesAmount(), summary.getTotalPaidOutAmount(), summary.getTotalPendingPayoutAmount());
        return toResponse(summary);
    }

    @Transactional
    public void recordSession(String guid, RecordSessionRequest request) {
        sessionActivityRepository.save(new UserSessionActivity(guid, request.sessionJti(), request.ipAddress(), request.userAgent()));
    }

    @Transactional
    public void logout(String guid, String jti) {
        if (jti == null) {
            return;
        }
        sessionActivityRepository.findByUserGuidAndSessionJti(guid, jti).ifPresent(UserSessionActivity::logout);
        log.debug("User {} logged out session {}", guid, jti);
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse adminDetail(String guid) {
        UserResponse user = userService.getMe(guid);
        BankingDetailResponse banking = bankingDetailRepository.findByUserGuid(guid).map(this::toResponse).orElse(null);
        return new AdminUserDetailResponse(user, banking != null, banking, getPayoutSummary(guid),
                sessionActivityRepository.findFirstByUserGuidOrderByLoginAtDesc(guid).map(UserSessionActivity::getLoginAt).orElse(null));
    }

    private BankingDetailResponse toResponse(UserBankingDetail detail) {
        String number = detail.getAccountNumber();
        String masked = number == null ? null : "****" + number.substring(Math.max(0, number.length() - 4));
        return new BankingDetailResponse(detail.getAccountHolderName(), detail.getBankName(), masked,
                detail.getBranchCode(), detail.getAccountType() == null ? null : detail.getAccountType().name(), detail.isVerified());
    }

    private PayoutSummaryResponse toResponse(UserPayoutSummary summary) {
        return new PayoutSummaryResponse(summary.getTotalSalesCount(), summary.getTotalSalesAmount(),
                summary.getTotalPaidOutAmount(), summary.getTotalPendingPayoutAmount(), summary.getLastPayoutAt());
    }
}

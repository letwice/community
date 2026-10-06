package community.store.product.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import community.store.common.client.UserClient;
import community.store.common.client.dto.UserSummaryResponse;
import community.store.common.web.ApiException;

@ExtendWith(MockitoExtension.class)
class AccountChecksTest {

    @Mock
    private UserClient userClient;

    @InjectMocks
    private AccountChecks checks;

    @Test
    void verifiedVendorWithBankingDetailsMayList() {
        when(userClient.getUser("v")).thenReturn(vendor("ACTIVE", "VERIFIED", true));

        assertThatCode(() -> checks.requireVerifiedSeller("v")).doesNotThrowAnyException();
    }

    @Test
    void vendorPendingReviewMayNotList() {
        when(userClient.getUser("v")).thenReturn(vendor("ACTIVE", "PENDING_REVIEW", true));

        assertThatThrownBy(() -> checks.requireVerifiedSeller("v")).isInstanceOf(ApiException.class).hasMessageContaining("verified");
    }

    @Test
    void verifiedVendorWithoutBankingDetailsMayNotList() {
        when(userClient.getUser("v")).thenReturn(vendor("ACTIVE", "VERIFIED", false));

        assertThatThrownBy(() -> checks.requireVerifiedSeller("v")).isInstanceOf(ApiException.class).hasMessageContaining("banking");
    }

    @Test
    void newVendorIsAskedForBankingDetailsFirst() {
        when(userClient.getUser("v")).thenReturn(vendor("ACTIVE", "PENDING_REVIEW", false));

        assertThatThrownBy(() -> checks.requireVerifiedSeller("v")).isInstanceOf(ApiException.class).hasMessageContaining("banking");
    }

    private static UserSummaryResponse vendor(String status, String verification, boolean banking) {
        return new UserSummaryResponse("v", "v@x", "V", "V", "VENDOR", status, verification, List.of("VENDOR"), banking);
    }
}

package community.store.product.service;

import org.springframework.stereotype.Component;

import community.store.common.client.UserClient;
import community.store.common.client.dto.UserSummaryResponse;
import community.store.common.web.ApiException;

/**
 * Cross-service account preconditions, resolved via service-user's /system API.
 */
@Component
public class AccountChecks {

    private final UserClient userClient;

    public AccountChecks(UserClient userClient) {
        this.userClient = userClient;
    }

    /**
     * Vendors/faculty may only list products once banking details are on file and the account is VERIFIED.
     * Banking details are checked first: it is the seller's own next step after verifying their email.
     */
    public UserSummaryResponse requireVerifiedSeller(String userGuid) {
        UserSummaryResponse user = userClient.getUser(userGuid);
        if (!user.hasBankingDetails()) {
            throw ApiException.forbidden("Add your banking details before listing products");
        }
        return requireVerified(user, "list products");
    }

    /** Community leaders/faculty may only publish activities once VERIFIED. */
    public UserSummaryResponse requireVerifiedOrganiser(String userGuid) {
        return requireVerified(userClient.getUser(userGuid), "post activities");
    }

    public UserSummaryResponse requireActive(String userGuid, String unavailableMessage) {
        UserSummaryResponse user = userClient.getUser(userGuid);
        if (!user.isActive()) {
            throw ApiException.conflict(unavailableMessage);
        }
        return user;
    }

    public UserSummaryResponse lookup(String userGuid) {
        return userClient.getUser(userGuid);
    }

    private static UserSummaryResponse requireVerified(UserSummaryResponse user, String action) {
        if (!user.isActive() || !user.isVerified()) {
            throw ApiException.forbidden("Your account must be verified by an admin before you can " + action
                    + " (current status: " + user.verificationStatus() + ")");
        }
        return user;
    }
}

package community.store.product.service;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import community.store.common.client.AdminNotifier;
import community.store.common.client.NotificationClient;
import community.store.product.config.MarketplaceProperties;
import community.store.product.domain.RatingStats;

/**
 * Alerts admins when a new review pushes a seller's or organiser's average rating below the low-rating threshold
 * (once they have enough reviews). It fires only on the crossing, not on every further low review.
 */
@Component
public class RatingWatch {

    private static final Logger log = LoggerFactory.getLogger(RatingWatch.class);

    private final MarketplaceProperties properties;
    private final AdminNotifier adminNotifier;

    public RatingWatch(MarketplaceProperties properties, AdminNotifier adminNotifier) {
        this.properties = properties;
        this.adminNotifier = adminNotifier;
    }

    /**
     * @param after     rating stats including the review that was just saved
     * @param newRating the rating of that review
     * @param role      "Seller" or "Event organiser", for the alert text
     */
    public void afterReview(String userGuid, String role, RatingStats after, int newRating) {
        long countAfter = after.getReviewCount();
        Double averageAfter = after.average();
        long countBefore = countAfter - 1;
        Double averageBefore = countBefore == 0 ? null : (double) (after.getRatingSum() - newRating) / countBefore;

        boolean lowNow = properties.isLowRating(averageAfter, countAfter);
        boolean lowBefore = properties.isLowRating(averageBefore, countBefore);
        if (lowNow && !lowBefore) {
            String average = String.format(Locale.ROOT, "%.1f", averageAfter);
            log.warn("{} {} dropped to a low average rating of {} from {} reviews", role, userGuid, average, countAfter);
            adminNotifier.notifyAdmins("Low rating: review account",
                    role + " now has an average rating of " + average + "★ from " + countAfter + " reviews (threshold "
                            + properties.getLowRatingThreshold() + "★). Review the account and block it if needed.",
                    NotificationClient.ENTITY_ACCOUNT, userGuid);
        }
    }
}

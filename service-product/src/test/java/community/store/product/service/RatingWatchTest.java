package community.store.product.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import community.store.common.client.AdminNotifier;
import community.store.common.client.NotificationClient;
import community.store.product.config.MarketplaceProperties;
import community.store.product.domain.RatingStats;

@ExtendWith(MockitoExtension.class)
class RatingWatchTest {

    @Mock
    private AdminNotifier adminNotifier;

    private final MarketplaceProperties properties = new MarketplaceProperties();

    @Test
    void alertsWhenAverageFirstDropsBelowThreshold() {
        // before: 3+2 from 2 reviews (avg 2.5, too few to judge) -> after a 1-star review: 6/3 = 2.0
        new RatingWatch(properties, adminNotifier).afterReview("v", "Seller", stats(6, 3), 1);

        verify(adminNotifier).notifyAdmins(eq("Low rating: review account"), contains("2.0★ from 3 reviews"),
                eq(NotificationClient.ENTITY_ACCOUNT), eq("v"));
    }

    @Test
    void doesNotAlertBeforeMinimumReviewCount() {
        new RatingWatch(properties, adminNotifier).afterReview("v", "Seller", stats(2, 2), 1);

        verify(adminNotifier, never()).notifyAdmins(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void doesNotRepeatTheAlertWhileStillLow() {
        // before: avg 2.0 from 3 (already low) -> after: 7/4 = 1.75
        new RatingWatch(properties, adminNotifier).afterReview("v", "Seller", stats(7, 4), 1);

        verify(adminNotifier, never()).notifyAdmins(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void doesNotAlertForHealthyRatings() {
        new RatingWatch(properties, adminNotifier).afterReview("v", "Seller", stats(18, 4), 5);

        verify(adminNotifier, never()).notifyAdmins(anyString(), anyString(), anyString(), anyString());
    }

    private static RatingStats stats(long sum, long count) {
        return new RatingStats() {
            @Override
            public long getRatingSum() {
                return sum;
            }

            @Override
            public long getReviewCount() {
                return count;
            }
        };
    }
}

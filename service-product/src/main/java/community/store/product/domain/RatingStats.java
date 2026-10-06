package community.store.product.domain;

/**
 * Sum and count of ratings (projection), so the average before/after a new review can be derived.
 */
public interface RatingStats {

    long getRatingSum();

    long getReviewCount();

    default Double average() {
        return getReviewCount() == 0 ? null : (double) getRatingSum() / getReviewCount();
    }
}

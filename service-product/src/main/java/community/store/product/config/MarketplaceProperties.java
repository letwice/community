package community.store.product.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "community.marketplace")
public class MarketplaceProperties {

    /**
     * false (default): when the buyer confirms receipt the order waits in DELIVERED_CONFIRMED until an admin
     * releases the funds. true: funds are released automatically on confirmation (the original TA behaviour).
     */
    private boolean autoReleaseFunds = false;
    /** Admins are alerted when a seller's or organiser's average rating drops below this value... */
    private double lowRatingThreshold = 2.5;
    /** ...once they have at least this many reviews. */
    private int lowRatingMinReviews = 3;

    public boolean isAutoReleaseFunds() {
        return autoReleaseFunds;
    }

    public void setAutoReleaseFunds(boolean autoReleaseFunds) {
        this.autoReleaseFunds = autoReleaseFunds;
    }

    public double getLowRatingThreshold() {
        return lowRatingThreshold;
    }

    public void setLowRatingThreshold(double lowRatingThreshold) {
        this.lowRatingThreshold = lowRatingThreshold;
    }

    public int getLowRatingMinReviews() {
        return lowRatingMinReviews;
    }

    public void setLowRatingMinReviews(int lowRatingMinReviews) {
        this.lowRatingMinReviews = lowRatingMinReviews;
    }

    public boolean isLowRating(Double average, long reviewCount) {
        return average != null && reviewCount >= lowRatingMinReviews && average < lowRatingThreshold;
    }
}

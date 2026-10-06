package community.store.product.dto;

import java.util.List;

public record ActivityDetailResponse(ActivityResponse activity, Double averageRating, long reviewCount, List<ReviewResponse> reviews) {
}

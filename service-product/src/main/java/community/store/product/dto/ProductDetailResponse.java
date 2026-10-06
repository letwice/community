package community.store.product.dto;

import java.util.List;

public record ProductDetailResponse(
        ProductResponse product,
        SellerResponse seller,
        Double averageRating,
        long reviewCount,
        List<ReviewResponse> reviews) {

    public record SellerResponse(String guid, String name, boolean verified) {
    }
}

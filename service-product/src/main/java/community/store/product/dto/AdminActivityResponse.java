package community.store.product.dto;

public record AdminActivityResponse(
        ActivityResponse activity,
        String organiserName,
        Double averageRating,
        long reviewCount) {
}

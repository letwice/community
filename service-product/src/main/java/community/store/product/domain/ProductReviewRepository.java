package community.store.product.domain;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    Page<ProductReview> findByProductGuid(String productGuid, Pageable pageable);

    boolean existsByOrderTransactionGuid(String orderTransactionGuid);

    @Query("select r.orderTransactionGuid from ProductReview r where r.orderTransactionGuid in :orderGuids")
    List<String> findReviewedOrderGuids(@Param("orderGuids") Collection<String> orderGuids);

    @Query("select avg(r.rating) from ProductReview r where r.productGuid = :productGuid")
    Double averageRating(@Param("productGuid") String productGuid);

    long countByProductGuid(String productGuid);

    @Query("""
            select coalesce(sum(r.rating), 0) as ratingSum, count(r) as reviewCount
            from ProductReview r, Product p where r.productGuid = p.guid and p.vendorUserGuid = :vendorGuid
            """)
    RatingStats statsForVendor(@Param("vendorGuid") String vendorGuid);

    @Query("""
            select p.vendorUserGuid as guid, coalesce(sum(r.rating), 0) as ratingSum, count(r) as reviewCount
            from ProductReview r, Product p where r.productGuid = p.guid group by p.vendorUserGuid
            """)
    List<GuidRatingStats> statsByVendor();

    @Query(value = """
            select r from ProductReview r, Product p
            where r.productGuid = p.guid and p.vendorUserGuid = :vendorGuid
            """,
            countQuery = """
            select count(r) from ProductReview r, Product p
            where r.productGuid = p.guid and p.vendorUserGuid = :vendorGuid
            """)
    Page<ProductReview> findByVendor(@Param("vendorGuid") String vendorGuid, Pageable pageable);
}

package community.store.product.domain;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityReviewRepository extends JpaRepository<ActivityReview, Long> {

    Page<ActivityReview> findByCommunityActivityGuid(String communityActivityGuid, Pageable pageable);

    boolean existsByCommunityActivityGuidAndReviewerUserGuid(String communityActivityGuid, String reviewerUserGuid);

    @Query("select avg(r.rating) from ActivityReview r where r.communityActivityGuid = :activityGuid")
    Double averageRating(@Param("activityGuid") String activityGuid);

    long countByCommunityActivityGuid(String communityActivityGuid);

    @Query("""
            select coalesce(sum(r.rating), 0) as ratingSum, count(r) as reviewCount
            from ActivityReview r, CommunityActivity a
            where r.communityActivityGuid = a.guid and a.organiserUserGuid = :organiserGuid
            """)
    RatingStats statsForOrganiser(@Param("organiserGuid") String organiserGuid);

    @Query("""
            select r.communityActivityGuid as guid, coalesce(sum(r.rating), 0) as ratingSum, count(r) as reviewCount
            from ActivityReview r where r.communityActivityGuid in :activityGuids group by r.communityActivityGuid
            """)
    List<GuidRatingStats> statsForActivities(@Param("activityGuids") Collection<String> activityGuids);
}

package community.store.product.domain;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityActivityRepository extends JpaRepository<CommunityActivity, Long> {

    Optional<CommunityActivity> findByGuid(String guid);

    Page<CommunityActivity> findByStatusAndActivityDateAfter(ActivityStatus status, LocalDateTime after, Pageable pageable);

    Page<CommunityActivity> findByStatusInAndActivityDateBefore(Collection<ActivityStatus> statuses, LocalDateTime before, Pageable pageable);

    Page<CommunityActivity> findByStatusIn(Collection<ActivityStatus> statuses, Pageable pageable);

    Page<CommunityActivity> findByOrganiserUserGuid(String organiserUserGuid, Pageable pageable);

    long countByStatus(ActivityStatus status);
}

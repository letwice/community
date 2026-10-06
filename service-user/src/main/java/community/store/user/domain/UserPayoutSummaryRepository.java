package community.store.user.domain;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserPayoutSummaryRepository extends JpaRepository<UserPayoutSummary, Long> {

    Optional<UserPayoutSummary> findByUserGuid(String userGuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserPayoutSummary s where s.userGuid = :userGuid")
    Optional<UserPayoutSummary> findByUserGuidForUpdate(@Param("userGuid") String userGuid);
}

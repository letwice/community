package community.store.user.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionActivityRepository extends JpaRepository<UserSessionActivity, Long> {

    Optional<UserSessionActivity> findByUserGuidAndSessionJti(String userGuid, String sessionJti);

    Optional<UserSessionActivity> findFirstByUserGuidOrderByLoginAtDesc(String userGuid);
}

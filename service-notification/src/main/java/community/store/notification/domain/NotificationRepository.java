package community.store.notification.domain;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            select n from Notification n
            where n.userGuid = :userGuid and n.type = :type
              and (:read is null or n.readFlag = :read)
            """)
    Page<Notification> findInbox(@Param("userGuid") String userGuid, @Param("type") NotificationType type,
                                 @Param("read") Boolean read, Pageable pageable);

    long countByUserGuidAndTypeAndReadFlagFalse(String userGuid, NotificationType type);

    Optional<Notification> findByIdAndUserGuid(Long id, String userGuid);
}

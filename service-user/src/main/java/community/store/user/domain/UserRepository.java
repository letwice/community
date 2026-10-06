package community.store.user.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByGuid(String guid);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Backoffice search. Every filter is optional; {@code prefix} must already be lower-case and end in {@code %}
     * (starts-with match on email, first or last name).
     */
    @Query("""
            select u from User u
            where (:verificationStatus is null or u.verificationStatus = :verificationStatus)
              and (:status is null or u.status = :status)
              and (:role is null or exists (select r.id from UserRole r where r.userGuid = u.guid and r.role = :role))
              and (:prefix is null or lower(u.email) like :prefix or lower(u.firstName) like :prefix or lower(u.lastName) like :prefix)
            """)
    Page<User> search(@Param("verificationStatus") VerificationStatus verificationStatus, @Param("status") UserStatus status,
                      @Param("role") Role role, @Param("prefix") String prefix, Pageable pageable);

    List<User> findByGuidIn(Collection<String> guids);
}

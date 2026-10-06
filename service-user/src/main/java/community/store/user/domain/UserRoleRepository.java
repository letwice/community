package community.store.user.domain;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findByUserGuid(String userGuid);

    List<UserRole> findByUserGuidIn(Collection<String> userGuids);

    List<UserRole> findByRole(Role role);
}

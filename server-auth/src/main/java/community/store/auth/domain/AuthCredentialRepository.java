package community.store.auth.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthCredentialRepository extends JpaRepository<AuthCredential, Long> {

    Optional<AuthCredential> findByUserGuid(String userGuid);
}

package community.store.auth.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthVerificationTokenRepository extends JpaRepository<AuthVerificationToken, Long> {

    Optional<AuthVerificationToken> findByTokenAndTokenType(String token, TokenType tokenType);
}

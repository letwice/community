package community.store.auth.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mocked verification token: logged/echoed instead of being emailed.
 */
@Entity
@Table(name = "auth_verification_token")
public class AuthVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_guid", nullable = false, length = 36)
    private String userGuid;

    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false)
    private TokenType tokenType;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    protected AuthVerificationToken() {
    }

    public AuthVerificationToken(String userGuid, String token, TokenType tokenType, LocalDateTime expiresAt) {
        this.userGuid = userGuid;
        this.token = token;
        this.tokenType = tokenType;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(LocalDateTime now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public void consume(LocalDateTime now) {
        this.consumedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getUserGuid() {
        return userGuid;
    }

    public String getToken() {
        return token;
    }

    public TokenType getTokenType() {
        return tokenType;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getConsumedAt() {
        return consumedAt;
    }
}

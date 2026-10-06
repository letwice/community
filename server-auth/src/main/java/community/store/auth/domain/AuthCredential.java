package community.store.auth.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_credential")
public class AuthCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_guid", nullable = false, unique = true, length = 36)
    private String userGuid;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    protected AuthCredential() {
    }

    public AuthCredential(String userGuid, String passwordHash) {
        this.userGuid = userGuid;
        this.passwordHash = passwordHash;
    }

    public void recordSuccessfulLogin() {
        this.failedLoginAttempts = 0;
        this.lastLoginAt = LocalDateTime.now();
    }

    public void recordFailedLogin() {
        this.failedLoginAttempts++;
    }

    public Long getId() {
        return id;
    }

    public String getUserGuid() {
        return userGuid;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }
}

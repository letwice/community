package community.store.user.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_session_activity")
public class UserSessionActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_guid", nullable = false, length = 36)
    private String userGuid;

    @Column(name = "session_jti", nullable = false, length = 64)
    private String sessionJti;

    @Column(name = "login_at", nullable = false)
    private LocalDateTime loginAt;

    @Column(name = "logout_at")
    private LocalDateTime logoutAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    protected UserSessionActivity() {
    }

    public UserSessionActivity(String userGuid, String sessionJti, String ipAddress, String userAgent) {
        this.userGuid = userGuid;
        this.sessionJti = sessionJti;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.loginAt = LocalDateTime.now();
        this.lastActivityAt = loginAt;
    }

    public void touch() {
        lastActivityAt = LocalDateTime.now();
    }

    public void logout() {
        logoutAt = LocalDateTime.now();
        lastActivityAt = logoutAt;
    }

    public String getUserGuid() {
        return userGuid;
    }

    public String getSessionJti() {
        return sessionJti;
    }

    public LocalDateTime getLoginAt() {
        return loginAt;
    }

    public LocalDateTime getLogoutAt() {
        return logoutAt;
    }
}

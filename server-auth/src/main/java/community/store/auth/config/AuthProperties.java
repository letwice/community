package community.store.auth.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "community.auth")
public class AuthProperties {

    private String issuer = "community-store-poc";
    private Duration accessTokenTtl = Duration.ofHours(1);
    private Duration refreshTokenTtl = Duration.ofHours(8);
    private Duration systemTokenTtl = Duration.ofMinutes(15);
    private Duration verificationTokenTtl = Duration.ofHours(24);
    private int maxFailedLoginAttempts = 5;
    /** Students and faculty must register with an address on exactly this domain. */
    private String universityEmailDomain = "university.com";
    /**
     * PoC convenience: echo the mocked email-verification token in the register response so the demo
     * clients can complete verification without a real mailbox. Must be false outside the PoC.
     */
    private boolean exposeVerificationToken = true;
    /** OAuth2 clients allowed to use the client_credentials grant (service-to-service). */
    private List<Client> clients = new ArrayList<>();

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public Duration getSystemTokenTtl() {
        return systemTokenTtl;
    }

    public void setSystemTokenTtl(Duration systemTokenTtl) {
        this.systemTokenTtl = systemTokenTtl;
    }

    public Duration getVerificationTokenTtl() {
        return verificationTokenTtl;
    }

    public void setVerificationTokenTtl(Duration verificationTokenTtl) {
        this.verificationTokenTtl = verificationTokenTtl;
    }

    public int getMaxFailedLoginAttempts() {
        return maxFailedLoginAttempts;
    }

    public void setMaxFailedLoginAttempts(int maxFailedLoginAttempts) {
        this.maxFailedLoginAttempts = maxFailedLoginAttempts;
    }

    public String getUniversityEmailDomain() {
        return universityEmailDomain;
    }

    public void setUniversityEmailDomain(String universityEmailDomain) {
        this.universityEmailDomain = universityEmailDomain;
    }

    public boolean isExposeVerificationToken() {
        return exposeVerificationToken;
    }

    public void setExposeVerificationToken(boolean exposeVerificationToken) {
        this.exposeVerificationToken = exposeVerificationToken;
    }

    public List<Client> getClients() {
        return clients;
    }

    public void setClients(List<Client> clients) {
        this.clients = clients;
    }

    public static class Client {
        private String id;
        private String secret;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }
}

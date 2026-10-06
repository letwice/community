package community.store.auth.service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import community.store.auth.config.AuthProperties;
import community.store.auth.domain.AuthCredential;
import community.store.auth.domain.AuthCredentialRepository;
import community.store.auth.domain.AuthVerificationToken;
import community.store.auth.domain.AuthVerificationTokenRepository;
import community.store.auth.domain.TokenType;
import community.store.auth.dto.AccessTokenResponse;
import community.store.auth.dto.RegisterRequest;
import community.store.auth.dto.RegisterResponse;
import community.store.auth.jwt.JwtService;
import community.store.auth.jwt.JwtService.MintedToken;
import community.store.common.client.NotificationClient;
import community.store.common.client.UserClient;
import community.store.common.client.dto.CreateUserRequest;
import community.store.common.client.dto.RecordSessionRequest;
import community.store.common.client.dto.UserSummaryResponse;
import community.store.common.security.Roles;
import community.store.common.web.ApiException;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String TOKEN_TYPE = "bearer";

    private final AuthCredentialRepository credentialRepository;
    private final AuthVerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserClient userClient;
    private final NotificationClient notificationClient;
    private final AuthProperties properties;
    private final RegistrationPolicy registrationPolicy;
    private final SecureRandom random = new SecureRandom();

    public AuthService(AuthCredentialRepository credentialRepository,
                       AuthVerificationTokenRepository verificationTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       UserClient userClient,
                       NotificationClient notificationClient,
                       AuthProperties properties,
                       RegistrationPolicy registrationPolicy) {
        this.credentialRepository = credentialRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userClient = userClient;
        this.notificationClient = notificationClient;
        this.properties = properties;
        this.registrationPolicy = registrationPolicy;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        registrationPolicy.validate(request);
        String businessNumber = request.businessNumber() == null ? null : request.businessNumber().trim();
        UserSummaryResponse user = userClient.createUser(new CreateUserRequest(
                request.email().trim().toLowerCase(), request.firstName(), request.lastName(), request.userType().name(),
                businessNumber));
        credentialRepository.save(new AuthCredential(user.guid(), passwordEncoder.encode(request.password())));

        String token = HexFormat.of().formatHex(randomBytes(32));
        verificationTokenRepository.save(new AuthVerificationToken(user.guid(), token, TokenType.EMAIL_VERIFY,
                LocalDateTime.now().plus(properties.getVerificationTokenTtl())));
        log.info("[SIMULATED EMAIL] Verification token for {} ({}): {}", user.email(), user.guid(), token);
        notificationClient.email(user.guid(), "Verify your Community Store email",
                "[SIMULATED] Use this token to verify your email: " + token,
                NotificationClient.ENTITY_ACCOUNT, user.guid());

        return new RegisterResponse(user.guid(), true, properties.isExposeVerificationToken() ? token : null);
    }

    @Transactional
    public void verifyEmail(String token) {
        LocalDateTime now = LocalDateTime.now();
        AuthVerificationToken verification = verificationTokenRepository.findByTokenAndTokenType(token, TokenType.EMAIL_VERIFY)
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> ApiException.badRequest("Verification token is invalid, expired or already used"));
        verification.consume(now);
        userClient.markEmailVerified(verification.getUserGuid());
        log.info("Email verified for user {}", verification.getUserGuid());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AccessTokenResponse passwordGrant(String username, String password, String ipAddress, String userAgent) {
        if (isBlank(username) || isBlank(password)) {
            throw ApiException.badRequest("username and password are required");
        }
        UserSummaryResponse user = lookupByEmail(username.trim().toLowerCase());
        AuthCredential credential = credentialRepository.findByUserGuid(user.guid())
                .orElseThrow(() -> ApiException.unauthorized("Invalid credentials"));

        if (credential.getFailedLoginAttempts() >= properties.getMaxFailedLoginAttempts()) {
            throw new ApiException(HttpStatus.LOCKED, "Account locked after too many failed login attempts");
        }
        if (!passwordEncoder.matches(password, credential.getPasswordHash())) {
            credential.recordFailedLogin();
            log.warn("Failed login for user {} (attempt {})", user.guid(), credential.getFailedLoginAttempts());
            throw ApiException.unauthorized("Invalid credentials");
        }
        if (!user.isActive()) {
            throw ApiException.forbidden("PENDING".equals(user.status())
                    ? "Email address not verified yet"
                    : "Account is " + user.status());
        }
        credential.recordSuccessfulLogin();
        IssuedTokens issued = issueUserTokens(user);
        recordSession(user.guid(), issued.accessJti(), ipAddress, userAgent);
        log.info("User {} logged in", user.guid());
        return issued.response();
    }

    public AccessTokenResponse refreshGrant(String refreshToken) {
        if (isBlank(refreshToken)) {
            throw ApiException.badRequest("refresh_token is required");
        }
        UserSummaryResponse user = userClient.getUser(jwtService.verifyRefreshToken(refreshToken));
        if (!user.isActive()) {
            throw ApiException.forbidden("Account is " + user.status());
        }
        return issueUserTokens(user).response();
    }

    public AccessTokenResponse clientCredentialsGrant(String clientId, String clientSecret) {
        boolean valid = properties.getClients().stream()
                .anyMatch(c -> c.getId().equals(clientId) && constantTimeEquals(c.getSecret(), clientSecret));
        if (!valid) {
            throw ApiException.unauthorized("Invalid client credentials");
        }
        List<String> roles = List.of(Roles.SYSTEM);
        MintedToken access = jwtService.mintAccessToken(clientId, roles, "system", null, properties.getSystemTokenTtl());
        return new AccessTokenResponse(access.token(), TOKEN_TYPE, null, access.expiresIn(), "system", roles);
    }

    /** Records session activity in service-user; best-effort so it never blocks login. */
    private void recordSession(String userGuid, String jti, String ipAddress, String userAgent) {
        try {
            userClient.recordSession(userGuid, new RecordSessionRequest(jti, ipAddress, truncate(userAgent, 255)));
        } catch (RuntimeException ex) {
            log.warn("Could not record session for user {}: {}", userGuid, ex.getMessage());
        }
    }

    private IssuedTokens issueUserTokens(UserSummaryResponse user) {
        MintedToken access = jwtService.mintAccessToken(user.guid(), user.roles(), "user",
                Map.of("email", user.email(), "name", user.displayName()), properties.getAccessTokenTtl());
        MintedToken refresh = jwtService.mintRefreshToken(user.guid());
        return new IssuedTokens(access.jti(),
                new AccessTokenResponse(access.token(), TOKEN_TYPE, refresh.token(), access.expiresIn(), "user", user.roles()));
    }

    private record IssuedTokens(String accessJti, AccessTokenResponse response) {
    }

    private UserSummaryResponse lookupByEmail(String email) {
        try {
            return userClient.findByEmail(email);
        } catch (ApiException ex) {
            if (ex.getStatus() == HttpStatus.NOT_FOUND) {
                throw ApiException.unauthorized("Invalid credentials");
            }
            throw ex;
        }
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}

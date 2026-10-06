package community.store.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import community.store.auth.config.AuthProperties;
import community.store.auth.domain.AuthCredential;
import community.store.auth.domain.AuthCredentialRepository;
import community.store.auth.domain.AuthVerificationToken;
import community.store.auth.domain.AuthVerificationTokenRepository;
import community.store.auth.domain.TokenType;
import community.store.auth.dto.AccessTokenResponse;
import community.store.auth.dto.RegisterRequest;
import community.store.auth.dto.RegisterResponse;
import community.store.auth.jwt.JwtKeyProvider;
import community.store.auth.jwt.JwtService;
import community.store.common.client.NotificationClient;
import community.store.common.client.UserClient;
import community.store.common.client.dto.UserSummaryResponse;
import community.store.common.web.ApiException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String GUID = "00000000-0000-0000-0000-000000000099";

    @Mock
    private AuthCredentialRepository credentialRepository;
    @Mock
    private AuthVerificationTokenRepository tokenRepository;
    @Mock
    private UserClient userClient;
    @Mock
    private NotificationClient notificationClient;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AuthProperties properties;
    private AuthService service;

    @BeforeEach
    void setUp() throws Exception {
        properties = new AuthProperties();
        AuthProperties.Client client = new AuthProperties.Client();
        client.setId("service-product");
        client.setSecret("secret");
        properties.setClients(List.of(client));
        service = new AuthService(credentialRepository, tokenRepository, encoder,
                new JwtService(new JwtKeyProvider(), properties), userClient, notificationClient, properties,
                new RegistrationPolicy(properties));
    }

    @Test
    void registerCreatesCredentialAndMockedVerificationToken() {
        when(userClient.createUser(any())).thenReturn(user("PENDING", "UNVERIFIED"));

        RegisterResponse response = service.register(new RegisterRequest("V@x.y", "Password123!", "V", "V", RegisterRequest.UserType.VENDOR, "2019/123456/07"));

        assertThat(response.userGuid()).isEqualTo(GUID);
        assertThat(response.verificationRequired()).isTrue();
        assertThat(response.simulatedVerificationToken()).hasSize(64);
        verify(credentialRepository).save(any(AuthCredential.class));
        verify(notificationClient).email(eq(GUID), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void studentWithNonUniversityEmailIsRejectedBeforeAnyUserIsCreated() {
        assertThatThrownBy(() -> service.register(new RegisterRequest("s@gmail.com", "Password123!", "S", "S",
                RegisterRequest.UserType.STUDENT, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("@university.com");
        verify(userClient, never()).createUser(any());
    }

    @Test
    void passwordGrantIssuesTokensWithRoles() {
        when(userClient.findByEmail("v@x.y")).thenReturn(user("ACTIVE", "VERIFIED"));
        when(credentialRepository.findByUserGuid(GUID)).thenReturn(Optional.of(new AuthCredential(GUID, encoder.encode("Password123!"))));

        AccessTokenResponse response = service.passwordGrant("V@x.y", "Password123!", "127.0.0.1", "test");

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.roles()).containsExactly("VENDOR");
    }

    @Test
    void wrongPasswordIncrementsFailedAttempts() {
        AuthCredential credential = new AuthCredential(GUID, encoder.encode("Password123!"));
        when(userClient.findByEmail("v@x.y")).thenReturn(user("ACTIVE", "VERIFIED"));
        when(credentialRepository.findByUserGuid(GUID)).thenReturn(Optional.of(credential));

        assertThatThrownBy(() -> service.passwordGrant("v@x.y", "nope", null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThat(credential.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void accountLocksAfterMaxFailedAttempts() {
        AuthCredential credential = new AuthCredential(GUID, encoder.encode("Password123!"));
        for (int i = 0; i < properties.getMaxFailedLoginAttempts(); i++) {
            credential.recordFailedLogin();
        }
        when(userClient.findByEmail("v@x.y")).thenReturn(user("ACTIVE", "VERIFIED"));
        when(credentialRepository.findByUserGuid(GUID)).thenReturn(Optional.of(credential));

        assertThatThrownBy(() -> service.passwordGrant("v@x.y", "Password123!", null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.LOCKED));
    }

    @Test
    void unverifiedEmailCannotLogIn() {
        when(userClient.findByEmail("v@x.y")).thenReturn(user("PENDING", "UNVERIFIED"));
        when(credentialRepository.findByUserGuid(GUID)).thenReturn(Optional.of(new AuthCredential(GUID, encoder.encode("Password123!"))));

        assertThatThrownBy(() -> service.passwordGrant("v@x.y", "Password123!", null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not verified");
    }

    @Test
    void unknownEmailLooksLikeBadCredentials() {
        when(userClient.findByEmail("x@x.y")).thenThrow(ApiException.notFound("User not found"));

        assertThatThrownBy(() -> service.passwordGrant("x@x.y", "whatever", null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void verifyEmailConsumesTokenOnce() {
        AuthVerificationToken token = new AuthVerificationToken(GUID, "t".repeat(64), TokenType.EMAIL_VERIFY, LocalDateTime.now().plusHours(1));
        when(tokenRepository.findByTokenAndTokenType(token.getToken(), TokenType.EMAIL_VERIFY)).thenReturn(Optional.of(token));

        service.verifyEmail(token.getToken());

        assertThat(token.getConsumedAt()).isNotNull();
        verify(userClient).markEmailVerified(GUID);
        assertThatThrownBy(() -> service.verifyEmail(token.getToken())).isInstanceOf(ApiException.class);
    }

    @Test
    void expiredVerificationTokenIsRejected() {
        AuthVerificationToken token = new AuthVerificationToken(GUID, "e".repeat(64), TokenType.EMAIL_VERIFY, LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByTokenAndTokenType(token.getToken(), TokenType.EMAIL_VERIFY)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verifyEmail(token.getToken())).isInstanceOf(ApiException.class);
        verify(userClient, never()).markEmailVerified(anyString());
    }

    @Test
    void clientCredentialsGrantIssuesSystemToken() {
        AccessTokenResponse response = service.clientCredentialsGrant("service-product", "secret");

        assertThat(response.roles()).containsExactly("SYSTEM");
        assertThat(response.refreshToken()).isNull();
        assertThatThrownBy(() -> service.clientCredentialsGrant("service-product", "wrong")).isInstanceOf(ApiException.class);
    }

    private static UserSummaryResponse user(String status, String verification) {
        return new UserSummaryResponse(GUID, "v@x.y", "V", "V", "VENDOR", status, verification, List.of("VENDOR"), false);
    }
}

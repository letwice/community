package community.store.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import community.store.auth.config.AuthProperties;
import community.store.common.web.ApiException;

class JwtServiceTest {

    private final JwtKeyProvider keyProvider = newKeyProvider();
    private final JwtService jwtService = new JwtService(keyProvider, new AuthProperties());

    @Test
    void accessTokenIsVerifiableWithPublishedKeyAndCarriesRoles() throws Exception {
        String token = jwtService.mintAccessToken("user-guid", List.of("VENDOR"), "user", Map.of("email", "v@x.y"), Duration.ofMinutes(5)).token();

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keyProvider.signingKey().toRSAPublicKey()).build();
        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("user-guid");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("VENDOR");
        assertThat(jwt.getId()).isNotBlank();
    }

    @Test
    void refreshTokenRoundTrips() {
        String refresh = jwtService.mintRefreshToken("user-guid").token();

        assertThat(jwtService.verifyRefreshToken(refresh)).isEqualTo("user-guid");
    }

    @Test
    void accessTokenCannotBeUsedAsRefreshToken() {
        String access = jwtService.mintAccessToken("user-guid", List.of("STUDENT"), "user", null, Duration.ofMinutes(5)).token();

        assertThatThrownBy(() -> jwtService.verifyRefreshToken(access)).isInstanceOf(ApiException.class);
    }

    @Test
    void tokenSignedByAnotherKeyIsRejected() {
        String foreign = new JwtService(newKeyProvider(), new AuthProperties()).mintRefreshToken("user-guid").token();

        assertThatThrownBy(() -> jwtService.verifyRefreshToken(foreign)).isInstanceOf(ApiException.class);
    }

    @Test
    void jwksExposesOnlyThePublicKey() {
        assertThat(keyProvider.publicJwkSet().getKeys()).hasSize(1);
        assertThat(keyProvider.publicJwkSet().getKeys().get(0).isPrivate()).isFalse();
    }

    private static JwtKeyProvider newKeyProvider() {
        try {
            return new JwtKeyProvider();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}

package community.store.auth.jwt;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import community.store.auth.config.AuthProperties;
import community.store.common.web.ApiException;

@Service
public class JwtService {

    static final String CLAIM_TOKEN_USE = "token_use";
    static final String USE_ACCESS = "access";
    static final String USE_REFRESH = "refresh";

    private final JwtKeyProvider keyProvider;
    private final AuthProperties properties;

    public JwtService(JwtKeyProvider keyProvider, AuthProperties properties) {
        this.keyProvider = keyProvider;
        this.properties = properties;
    }

    public MintedToken mintAccessToken(String subject, List<String> roles, String scope, Map<String, Object> extraClaims, Duration ttl) {
        JWTClaimsSet.Builder claims = baseClaims(subject, ttl)
                .claim(CLAIM_TOKEN_USE, USE_ACCESS)
                .claim("roles", roles)
                .claim("scope", scope);
        if (extraClaims != null) {
            extraClaims.forEach(claims::claim);
        }
        return sign(claims.build(), ttl);
    }

    public MintedToken mintRefreshToken(String subject) {
        Duration ttl = properties.getRefreshTokenTtl();
        return sign(baseClaims(subject, ttl).claim(CLAIM_TOKEN_USE, USE_REFRESH).build(), ttl);
    }

    /**
     * Validates a refresh token's signature, expiry and use, returning its subject (user guid).
     */
    public String verifyRefreshToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new RSASSAVerifier(keyProvider.signingKey().toRSAPublicKey()))) {
                throw ApiException.unauthorized("Invalid refresh token");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) {
                throw ApiException.unauthorized("Refresh token expired");
            }
            if (!USE_REFRESH.equals(claims.getStringClaim(CLAIM_TOKEN_USE))) {
                throw ApiException.unauthorized("Not a refresh token");
            }
            return claims.getSubject();
        } catch (ParseException | JOSEException ex) {
            throw ApiException.unauthorized("Invalid refresh token");
        }
    }

    private JWTClaimsSet.Builder baseClaims(String subject, Duration ttl) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(properties.getIssuer())
                .subject(subject)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)));
    }

    private MintedToken sign(JWTClaimsSet claims, Duration ttl) {
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(keyProvider.signingKey().getKeyID())
                    .type(JOSEObjectType.JWT)
                    .build();
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(keyProvider.signingKey()));
            return new MintedToken(jwt.serialize(), claims.getJWTID(), ttl.toSeconds());
        } catch (JOSEException ex) {
            throw new IllegalStateException("Failed to sign JWT", ex);
        }
    }

    public record MintedToken(String token, String jti, long expiresIn) {
    }
}

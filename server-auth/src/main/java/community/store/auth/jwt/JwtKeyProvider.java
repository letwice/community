package community.store.auth.jwt;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

/**
 * RSA signing key, generated at startup. PoC trade-off: restarting server-auth invalidates
 * outstanding tokens (resource servers re-fetch the JWKS automatically on an unknown key id).
 */
@Component
public class JwtKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);

    private final RSAKey signingKey;

    public JwtKeyProvider() throws JOSEException {
        this.signingKey = new RSAKeyGenerator(2048)
                .keyID(UUID.randomUUID().toString())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .generate();
        log.info("Generated RSA signing key {}", signingKey.getKeyID());
    }

    public RSAKey signingKey() {
        return signingKey;
    }

    public JWKSet publicJwkSet() {
        return new JWKSet(signingKey.toPublicJWK());
    }
}

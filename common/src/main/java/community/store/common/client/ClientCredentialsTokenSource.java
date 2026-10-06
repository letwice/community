package community.store.common.client;

import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import community.store.common.CommonProperties;

/**
 * Obtains (and caches until shortly before expiry) a client_credentials token from server-auth.
 */
public class ClientCredentialsTokenSource implements SystemTokenSource {

    private static final Logger log = LoggerFactory.getLogger(ClientCredentialsTokenSource.class);
    private static final long EXPIRY_SKEW_SECONDS = 30;

    private final CommonProperties properties;
    private final RestClient restClient;
    private String cachedToken;
    private Instant expiresAt = Instant.EPOCH;

    public ClientCredentialsTokenSource(CommonProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.getServices().getAuthUrl()).build();
    }

    @Override
    public synchronized String token() {
        if (cachedToken != null && Instant.now().isBefore(expiresAt)) {
            return cachedToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getSystemClient().getClientId());
        form.add("client_secret", properties.getSystemClient().getClientSecret());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        if (response == null || response.get("access_token") == null) {
            throw new IllegalStateException("server-auth returned no access token for client " + properties.getSystemClient().getClientId());
        }
        cachedToken = (String) response.get("access_token");
        long expiresIn = ((Number) response.getOrDefault("expires_in", 300)).longValue();
        expiresAt = Instant.now().plusSeconds(Math.max(0, expiresIn - EXPIRY_SKEW_SECONDS));
        log.debug("Obtained system token for client {} (expires in {}s)", properties.getSystemClient().getClientId(), expiresIn);
        return cachedToken;
    }
}

package community.store.ui.desktop.api;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

/**
 * Thin REST client for the Community Store services. Base URLs can be overridden with system properties
 * ({@code -Dcommunity.auth-url=...}, {@code community.user-url}, {@code community.notification-url},
 * {@code community.product-url}).
 */
public class ApiClient {

    public final String authUrl = System.getProperty("community.auth-url", "http://localhost:9600/auth");
    public final String userUrl = System.getProperty("community.user-url", "http://localhost:9601/service-user");
    public final String notificationUrl = System.getProperty("community.notification-url", "http://localhost:9602/service-notification");
    public final String productUrl = System.getProperty("community.product-url", "http://localhost:9603/service-product");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private Session session;

    public Session session() {
        return session;
    }

    public Session login(String email, String password) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "password");
        form.put("username", email);
        form.put("password", password);
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(authUrl + "/oauth/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        JsonNode token = send(request);
        session = Session.fromAccessToken(token.path("access_token").asText(), mapper);
        return session;
    }

    public void logout() {
        if (session != null) {
            try {
                post(userUrl + "/frontend/users/me/logout", null);
            } catch (RuntimeException ignored) {
                // best effort: the token simply expires
            }
        }
        session = null;
    }

    public JsonNode register(Map<String, Object> request) {
        return send(jsonRequest(authUrl + "/register", "POST", request, false));
    }

    public void verifyEmail(String token) {
        send(jsonRequest(authUrl + "/verify-email", "POST", Map.of("token", token), false));
    }

    public JsonNode get(String url) {
        return send(authorised(HttpRequest.newBuilder(URI.create(url)).GET()).build());
    }

    public JsonNode post(String url, Object body) {
        return send(jsonRequest(url, "POST", body, true));
    }

    public JsonNode put(String url, Object body) {
        return send(jsonRequest(url, "PUT", body, true));
    }

    public JsonNode delete(String url) {
        return send(authorised(HttpRequest.newBuilder(URI.create(url)).DELETE()).build());
    }

    public static String query(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private HttpRequest jsonRequest(String url, String method, Object body, boolean auth) {
        try {
            HttpRequest.BodyPublisher publisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body));
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .method(method, publisher);
            return (auth ? authorised(builder) : builder).build();
        } catch (IOException ex) {
            throw new ApiException(0, "Could not serialise request: " + ex.getMessage());
        }
    }

    private HttpRequest.Builder authorised(HttpRequest.Builder builder) {
        if (session == null) {
            throw new ApiException(401, "Not logged in");
        }
        return builder.header("Authorization", "Bearer " + session.accessToken()).timeout(Duration.ofSeconds(15));
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (response.statusCode() >= 400) {
                throw new ApiException(response.statusCode(), errorMessage(response.statusCode(), body));
            }
            return body == null || body.isBlank() ? NullNode.getInstance() : mapper.readTree(body);
        } catch (IOException ex) {
            throw new ApiException(0, "Cannot reach " + request.uri().getHost() + ":" + request.uri().getPort()
                    + " — is the backend running? (docker compose up)");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "Request interrupted");
        }
    }

    private String errorMessage(int status, String body) {
        try {
            JsonNode error = mapper.readTree(body);
            String message = error.path("message").asText("HTTP " + status);
            if (error.path("details").isArray() && !error.path("details").isEmpty()) {
                StringBuilder sb = new StringBuilder(message);
                error.path("details").forEach(d -> sb.append("\n • ").append(d.asText()));
                return sb.toString();
            }
            return message;
        } catch (IOException | RuntimeException ex) {
            return "HTTP " + status;
        }
    }

    /** Decodes the (already server-validated) JWT payload for display purposes only. */
    static JsonNode decodePayload(String jwt, ObjectMapper mapper) {
        try {
            String[] parts = jwt.split("\\.");
            return mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
        } catch (IOException | RuntimeException ex) {
            throw new ApiException(0, "Unreadable access token");
        }
    }
}

package community.store.common.client;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.web.client.RestClient;

import community.store.common.client.dto.CreateUserRequest;
import community.store.common.client.dto.PayoutDeltaRequest;
import community.store.common.client.dto.RecordSessionRequest;
import community.store.common.client.dto.UserSummaryResponse;

/**
 * Client for {@code service-user} /system/* endpoints.
 */
public class UserClient {

    private final RestClient restClient;

    public UserClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public UserSummaryResponse getUser(String guid) {
        return restClient.get().uri("/system/users/{guid}", guid).retrieve().body(UserSummaryResponse.class);
    }

    /** Batch lookup; returns an empty map for an empty input. */
    public Map<String, UserSummaryResponse> getUsers(Collection<String> guids) {
        if (guids == null || guids.isEmpty()) {
            return Map.of();
        }
        UserSummaryResponse[] users = restClient.get()
                .uri(b -> b.path("/system/users").queryParam("guid", guids.toArray()).build())
                .retrieve().body(UserSummaryResponse[].class);
        return users == null ? Map.of() : Arrays.stream(users).collect(Collectors.toMap(UserSummaryResponse::guid, Function.identity()));
    }

    public List<String> adminGuids() {
        String[] guids = restClient.get().uri("/system/users/admins").retrieve().body(String[].class);
        return guids == null ? List.of() : List.of(guids);
    }

    public UserSummaryResponse findByEmail(String email) {
        return restClient.get().uri(b -> b.path("/system/users/by-email").queryParam("email", email).build())
                .retrieve().body(UserSummaryResponse.class);
    }

    public UserSummaryResponse createUser(CreateUserRequest request) {
        return restClient.post().uri("/system/users").body(request).retrieve().body(UserSummaryResponse.class);
    }

    public UserSummaryResponse markEmailVerified(String guid) {
        return restClient.post().uri("/system/users/{guid}/email-verified", guid).retrieve().body(UserSummaryResponse.class);
    }

    public void recordSession(String guid, RecordSessionRequest request) {
        restClient.post().uri("/system/users/{guid}/sessions", guid).body(request).retrieve().toBodilessEntity();
    }

    public void applyPayoutDelta(String guid, PayoutDeltaRequest request) {
        restClient.put().uri("/system/users/{guid}/payout-summary", guid).body(request).retrieve().toBodilessEntity();
    }
}

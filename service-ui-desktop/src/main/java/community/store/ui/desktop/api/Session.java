package community.store.ui.desktop.api;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public record Session(String accessToken, String userGuid, String name, String email, Set<String> roles) {

    static Session fromAccessToken(String accessToken, ObjectMapper mapper) {
        JsonNode claims = ApiClient.decodePayload(accessToken, mapper);
        Set<String> roles = new LinkedHashSet<>();
        claims.path("roles").forEach(r -> roles.add(r.asText()));
        return new Session(accessToken, claims.path("sub").asText(), claims.path("name").asText(),
                claims.path("email").asText(), roles);
    }

    public boolean has(String role) {
        return roles.contains(role);
    }

    public boolean canSell() {
        return has("VENDOR") || has("FACULTY");
    }

    public boolean canOrganise() {
        return has("COMMUNITY_LEADER") || has("FACULTY");
    }

    public boolean isAdmin() {
        return has("ADMIN");
    }
}

package community.store.common.security;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import community.store.common.web.ApiException;

/**
 * Accessors for the authenticated principal. The JWT {@code sub} claim is the user's immutable guid.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static String guid() {
        return authentication().getName();
    }

    public static Set<String> roles() {
        return authentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .collect(Collectors.toSet());
    }

    public static boolean hasRole(String role) {
        return roles().contains(role);
    }

    /** The token id ({@code jti}), used to correlate session activity. */
    public static String tokenId() {
        if (authentication() instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getId();
        }
        return null;
    }

    private static Authentication authentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw ApiException.unauthorized("Not authenticated");
        }
        return auth;
    }
}

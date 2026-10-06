package community.store.auth.web;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import community.store.auth.dto.AccessTokenResponse;
import community.store.auth.dto.RegisterRequest;
import community.store.auth.dto.RegisterResponse;
import community.store.auth.dto.VerifyEmailRequest;
import community.store.auth.jwt.JwtKeyProvider;
import community.store.auth.service.AuthService;
import community.store.common.web.ApiException;

/**
 * Served under the {@code /auth} context path.
 */
@RestController
public class AuthController {

    private final AuthService authService;
    private final JwtKeyProvider keyProvider;

    public AuthController(AuthService authService, JwtKeyProvider keyProvider) {
        this.authService = authService;
        this.keyProvider = keyProvider;
    }

    @PostMapping(path = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public AccessTokenResponse token(@RequestParam Map<String, String> form, HttpServletRequest request) {
        String grantType = form.get("grant_type");
        if (grantType == null) {
            throw ApiException.badRequest("grant_type is required");
        }
        return switch (grantType) {
            case "password" -> authService.passwordGrant(form.get("username"), form.get("password"),
                    request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
            case "client_credentials" -> authService.clientCredentialsGrant(form.get("client_id"), form.get("client_secret"));
            case "refresh_token" -> authService.refreshGrant(form.get("refresh_token"));
            default -> throw ApiException.badRequest("Unsupported grant_type: " + grantType);
        };
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
    }

    @GetMapping(path = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        return keyProvider.publicJwkSet().toJSONObject();
    }
}

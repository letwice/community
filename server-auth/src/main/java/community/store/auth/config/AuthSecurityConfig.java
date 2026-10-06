package community.store.auth.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

import community.store.auth.jwt.JwtService;
import community.store.common.client.SystemTokenSource;
import community.store.common.security.Roles;

/**
 * server-auth is the token issuer: every endpoint it exposes is public (credentials travel in the body).
 */
@Configuration
public class AuthSecurityConfig {

    @Bean
    public SecurityFilterChain authFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** server-auth signs its own SYSTEM token rather than calling its own token endpoint. */
    @Bean
    public SystemTokenSource systemTokenSource(JwtService jwtService, AuthProperties properties) {
        return () -> jwtService.mintAccessToken("server-auth", List.of(Roles.SYSTEM), "system", null, properties.getSystemTokenTtl()).token();
    }
}

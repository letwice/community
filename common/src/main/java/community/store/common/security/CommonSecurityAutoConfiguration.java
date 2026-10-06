package community.store.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

import community.store.common.CommonProperties;

/**
 * OAuth2 resource-server setup shared by service-user, service-notification and service-product.
 * Role claims become {@code ROLE_*} authorities; {@code /system/**} is reserved for SYSTEM (client_credentials) tokens.
 * Fine-grained rules live in {@code @PreAuthorize} on controllers.
 */
@AutoConfiguration
@EnableMethodSecurity
@EnableConfigurationProperties(CommonProperties.class)
@ConditionalOnProperty(prefix = "community.security", name = "resource-server", havingValue = "true", matchIfMissing = true)
public class CommonSecurityAutoConfiguration {

    @Bean
    public SecurityFilterChain resourceServerFilterChain(HttpSecurity http, CommonProperties properties) throws Exception {
        String[] publicPaths = properties.getSecurity().getPublicPaths().toArray(String[]::new);
        http.csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(publicPaths).permitAll()
                        .requestMatchers("/system/**").hasRole(Roles.SYSTEM)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    public static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}

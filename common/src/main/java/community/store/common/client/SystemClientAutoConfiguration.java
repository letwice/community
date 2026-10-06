package community.store.common.client;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import community.store.common.CommonProperties;

/**
 * Service-to-service REST clients. server-auth overrides {@link SystemTokenSource} to mint its own token.
 */
@AutoConfiguration(after = RestClientAutoConfiguration.class)
@EnableConfigurationProperties(CommonProperties.class)
public class SystemClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SystemTokenSource systemTokenSource(CommonProperties properties, RestClient.Builder builder) {
        return new ClientCredentialsTokenSource(properties, builder.clone());
    }

    @Bean
    @ConditionalOnMissingBean
    public SystemRestClients systemRestClients(RestClient.Builder builder, SystemTokenSource tokenSource, ObjectMapper objectMapper) {
        return new SystemRestClients(builder, tokenSource, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public UserClient userClient(SystemRestClients clients, CommonProperties properties) {
        return new UserClient(clients.forBaseUrl(properties.getServices().getUserUrl()));
    }

    @Bean
    @ConditionalOnMissingBean
    public AdminNotifier adminNotifier(UserClient userClient, NotificationClient notificationClient) {
        return new AdminNotifier(userClient, notificationClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public NotificationClient notificationClient(SystemRestClients clients, CommonProperties properties) {
        return new NotificationClient(clients.forBaseUrl(properties.getServices().getNotificationUrl()));
    }
}

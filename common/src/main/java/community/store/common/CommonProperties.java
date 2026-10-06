package community.store.common;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared configuration for every Community Store service, bound from the {@code community.*} prefix.
 */
@ConfigurationProperties(prefix = "community")
public class CommonProperties {

    private final Security security = new Security();
    private final Cors cors = new Cors();
    private final Services services = new Services();
    private final SystemClient systemClient = new SystemClient();

    public Security getSecurity() {
        return security;
    }

    public Cors getCors() {
        return cors;
    }

    public Services getServices() {
        return services;
    }

    public SystemClient getSystemClient() {
        return systemClient;
    }

    public static class Security {
        /** When true, the service validates JWT bearer tokens issued by server-auth. */
        private boolean resourceServer = true;
        /** Ant-style paths (relative to the context path) that do not require a token. */
        private List<String> publicPaths = new ArrayList<>();

        public boolean isResourceServer() {
            return resourceServer;
        }

        public void setResourceServer(boolean resourceServer) {
            this.resourceServer = resourceServer;
        }

        public List<String> getPublicPaths() {
            return publicPaths;
        }

        public void setPublicPaths(List<String> publicPaths) {
            this.publicPaths = publicPaths;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:9700", "http://127.0.0.1:9700"));

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Services {
        private String authUrl = "http://localhost:9600/auth";
        private String userUrl = "http://localhost:9601/service-user";
        private String notificationUrl = "http://localhost:9602/service-notification";
        private String productUrl = "http://localhost:9603/service-product";

        public String getAuthUrl() {
            return authUrl;
        }

        public void setAuthUrl(String authUrl) {
            this.authUrl = authUrl;
        }

        public String getUserUrl() {
            return userUrl;
        }

        public void setUserUrl(String userUrl) {
            this.userUrl = userUrl;
        }

        public String getNotificationUrl() {
            return notificationUrl;
        }

        public void setNotificationUrl(String notificationUrl) {
            this.notificationUrl = notificationUrl;
        }

        public String getProductUrl() {
            return productUrl;
        }

        public void setProductUrl(String productUrl) {
            this.productUrl = productUrl;
        }
    }

    public static class SystemClient {
        /** OAuth2 client_credentials identity this service uses for /system/* calls. */
        private String clientId;
        private String clientSecret;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }
    }
}

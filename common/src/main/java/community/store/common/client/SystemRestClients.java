package community.store.common.client;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import community.store.common.web.ApiErrorResponse;
import community.store.common.web.ApiException;

/**
 * Builds {@link RestClient}s for /system/* calls: attaches the SYSTEM bearer token and converts
 * downstream 4xx errors back into {@link ApiException}s so the caller can propagate them.
 */
public class SystemRestClients {

    private static final Logger log = LoggerFactory.getLogger(SystemRestClients.class);

    private final RestClient.Builder builder;
    private final SystemTokenSource tokenSource;
    private final ObjectMapper objectMapper;

    public SystemRestClients(RestClient.Builder builder, SystemTokenSource tokenSource, ObjectMapper objectMapper) {
        this.builder = builder;
        this.tokenSource = tokenSource;
        this.objectMapper = objectMapper;
    }

    public RestClient forBaseUrl(String baseUrl) {
        return builder.clone()
                .baseUrl(baseUrl)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + tokenSource.token());
                    return execution.execute(request, body);
                })
                .defaultStatusHandler(status -> status.is4xxClientError(), (request, response) -> {
                    HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());
                    throw new ApiException(status, readMessage(response.getBody().readAllBytes(), status));
                })
                .defaultStatusHandler(status -> status.is5xxServerError(), (request, response) -> {
                    log.warn("Downstream {} {} failed with {}", request.getMethod(), request.getURI(), response.getStatusCode());
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "A dependent service failed");
                })
                .build();
    }

    private String readMessage(byte[] body, HttpStatus status) {
        try {
            ApiErrorResponse error = objectMapper.readValue(body, ApiErrorResponse.class);
            if (error.message() != null) {
                return error.message();
            }
        } catch (IOException ignored) {
            // fall through to the generic reason phrase
        }
        return status.getReasonPhrase();
    }
}

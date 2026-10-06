package community.store.common.client;

/**
 * Supplies a bearer token carrying the SYSTEM role for /system/* service-to-service calls.
 */
@FunctionalInterface
public interface SystemTokenSource {

    String token();
}

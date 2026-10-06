package community.store.common.security;

/**
 * Role names carried in the JWT {@code roles} claim (granted as {@code ROLE_<name>}).
 */
public final class Roles {

    public static final String STUDENT = "STUDENT";
    public static final String VENDOR = "VENDOR";
    public static final String COMMUNITY_LEADER = "COMMUNITY_LEADER";
    public static final String FACULTY = "FACULTY";
    public static final String ADMIN = "ADMIN";
    /** Granted only to client_credentials tokens used for /system/* service-to-service calls. */
    public static final String SYSTEM = "SYSTEM";

    private Roles() {
    }
}

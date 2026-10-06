package community.store.auth.service;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import community.store.auth.config.AuthProperties;
import community.store.auth.dto.RegisterRequest;
import community.store.auth.dto.RegisterRequest.UserType;
import community.store.common.web.ApiException;

/**
 * Registration rules that depend on the account type:
 * <ul>
 *   <li>Students and faculty must use an address on the university domain (exact match, no subdomains).</li>
 *   <li>Vendors must supply a business registration number. Other types may not.</li>
 * </ul>
 */
@Component
public class RegistrationPolicy {

    private static final Set<UserType> UNIVERSITY_MEMBERS = EnumSet.of(UserType.STUDENT, UserType.FACULTY);

    private final AuthProperties properties;

    public RegistrationPolicy(AuthProperties properties) {
        this.properties = properties;
    }

    public void validate(RegisterRequest request) {
        String domain = properties.getUniversityEmailDomain().toLowerCase(Locale.ROOT);
        if (UNIVERSITY_MEMBERS.contains(request.userType()) && !hasDomain(request.email(), domain)) {
            throw ApiException.badRequest("Students and faculty must register with an @" + domain + " email address");
        }
        boolean hasBusinessNumber = request.businessNumber() != null && !request.businessNumber().isBlank();
        if (request.userType() == UserType.VENDOR && !hasBusinessNumber) {
            throw ApiException.badRequest("Vendors must provide a business registration number");
        }
        if (request.userType() != UserType.VENDOR && hasBusinessNumber) {
            throw ApiException.badRequest("Only vendors provide a business registration number");
        }
    }

    static boolean hasDomain(String email, String domain) {
        String normalised = email.trim().toLowerCase(Locale.ROOT);
        int at = normalised.lastIndexOf('@');
        return at > 0 && normalised.substring(at + 1).equals(domain);
    }
}

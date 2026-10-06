package community.store.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import community.store.auth.config.AuthProperties;
import community.store.auth.dto.RegisterRequest;
import community.store.auth.dto.RegisterRequest.UserType;
import community.store.common.web.ApiException;

class RegistrationPolicyTest {

    private final RegistrationPolicy policy = new RegistrationPolicy(new AuthProperties());

    @ParameterizedTest
    @EnumSource(value = UserType.class, names = {"STUDENT", "FACULTY"})
    void studentsAndFacultyMayUseTheUniversityDomain(UserType type) {
        assertThatCode(() -> policy.validate(request("thandi@University.COM", type, null))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"s@gmail.com", "s@student.university.com", "s@university.com.evil.org", "s@notuniversity.com",
            "university.com@gmail.com"})
    void studentsAndFacultyMayNotUseOtherDomains(String email) {
        for (UserType type : new UserType[]{UserType.STUDENT, UserType.FACULTY}) {
            assertThatThrownBy(() -> policy.validate(request(email, type, null)))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("@university.com");
        }
    }

    @Test
    void vendorsAndCommunityLeadersMayUseAnyDomain() {
        assertThatCode(() -> policy.validate(request("v@gmail.com", UserType.VENDOR, "2019/123456/07"))).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate(request("l@gmail.com", UserType.COMMUNITY_LEADER, null))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void vendorsMustProvideABusinessNumber(String businessNumber) {
        assertThatThrownBy(() -> policy.validate(request("v@gmail.com", UserType.VENDOR, businessNumber)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("business registration number");
        assertThatThrownBy(() -> policy.validate(request("v@gmail.com", UserType.VENDOR, null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void onlyVendorsProvideABusinessNumber() {
        assertThatThrownBy(() -> policy.validate(request("l@gmail.com", UserType.COMMUNITY_LEADER, "2019/123456/07")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void domainIsConfigurable() {
        AuthProperties properties = new AuthProperties();
        properties.setUniversityEmailDomain("wits.ac.za");
        RegistrationPolicy custom = new RegistrationPolicy(properties);

        assertThatCode(() -> custom.validate(request("s@wits.ac.za", UserType.STUDENT, null))).doesNotThrowAnyException();
        assertThatThrownBy(() -> custom.validate(request("s@university.com", UserType.STUDENT, null))).isInstanceOf(ApiException.class);
    }

    private static RegisterRequest request(String email, UserType type, String businessNumber) {
        return new RegisterRequest(email, "Password123!", "First", "Last", type, businessNumber);
    }
}

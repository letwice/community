package community.store.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void newRegistrationIsPendingAndUnverified() {
        User user = User.register("a@b.c", "A", "B", UserType.VENDOR, "2019/123456/07");

        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(user.getVerificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
        assertThat(user.getGuid()).hasSize(36);
    }

    @Test
    void studentIsVerifiedOnEmailVerification() {
        User student = User.register("s@university.com", "S", "S", UserType.STUDENT, null);

        student.markEmailVerified();

        assertThat(student.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(student.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
    }

    @Test
    void vendorFacultyAndLeaderGoToAdminReviewQueue() {
        for (UserType type : new UserType[]{UserType.VENDOR, UserType.FACULTY, UserType.COMMUNITY_LEADER}) {
            User user = User.register(type + "@x.y", "F", "L", type, null);

            user.markEmailVerified();

            assertThat(user.getStatus()).as(type.name()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.getVerificationStatus()).as(type.name()).isEqualTo(VerificationStatus.PENDING_REVIEW);
        }
    }

    @Test
    void repeatedEmailVerificationDoesNotUndoAdminDecision() {
        User vendor = User.register("v@x.y", "V", "V", UserType.VENDOR, "2019/123456/07");
        vendor.markEmailVerified();
        vendor.reject();

        vendor.markEmailVerified();

        assertThat(vendor.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
    }
}

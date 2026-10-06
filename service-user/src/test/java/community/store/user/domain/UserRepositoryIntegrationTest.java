package community.store.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import community.store.user.config.AccountNumberConverter;
import community.store.user.config.UserProperties;

/**
 * Runs the real Flyway migrations (schema + seed) against MySQL 8. Skipped when Docker is not available.
 */
@DataJpaTest(properties = "community.user.encryption-key=GI78pck3q9utqwEFugNtpndt05HZEKvIKR6D499L3Xk=")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(AccountNumberConverter.class)
@org.springframework.boot.context.properties.EnableConfigurationProperties(UserProperties.class)
class UserRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserBankingDetailRepository bankingDetailRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    void verificationQueueFiltersByStatusAndRole() {
        assertThat(userRepository.search(VerificationStatus.PENDING_REVIEW, null, Role.VENDOR, null, PageRequest.of(0, 10)).getContent())
                .extracting(User::getEmail).containsExactly("pending.vendor@community.store");
        assertThat(userRepository.search(VerificationStatus.PENDING_REVIEW, null, Role.FACULTY, null, PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    void searchMatchesEmailOrNamePrefixAndAccountStatus() {
        assertThat(userRepository.search(null, null, null, "thandi%", PageRequest.of(0, 10)).getContent())
                .extracting(User::getEmail).containsExactly("faculty@university.com");
        assertThat(userRepository.search(null, null, null, "student@%", PageRequest.of(0, 10)).getContent()).hasSize(1);
        assertThat(userRepository.search(null, UserStatus.SUSPENDED, null, null, PageRequest.of(0, 10)).getContent()).isEmpty();
        assertThat(userRepository.search(null, UserStatus.ACTIVE, null, null, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(6);
    }

    @Test
    void findsAdminsByRole() {
        assertThat(userRoleRepository.findByRole(Role.ADMIN)).extracting(UserRole::getUserGuid)
                .containsExactly("00000000-0000-0000-0000-000000000001");
    }

    @Test
    void seededBankingDetailsDecrypt() {
        assertThat(bankingDetailRepository.findByUserGuid("00000000-0000-0000-0000-000000000003"))
                .get().extracting(UserBankingDetail::getAccountNumber).isEqualTo("62000012345");
    }
}

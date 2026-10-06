package community.store.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import community.store.common.client.NotificationClient;
import community.store.common.client.dto.CreateUserRequest;
import community.store.common.web.ApiException;
import community.store.user.domain.Role;
import community.store.user.domain.User;
import community.store.user.domain.UserRole;
import community.store.user.domain.UserStatus;
import community.store.user.domain.UserBankingDetailRepository;
import community.store.user.domain.UserRepository;
import community.store.user.domain.UserRoleRepository;
import community.store.user.domain.UserType;
import community.store.user.domain.VerificationStatus;
import community.store.user.dto.UserResponse;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserRoleRepository roleRepository;
    @Mock
    private UserBankingDetailRepository bankingDetailRepository;
    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private UserService service;

    @Test
    void duplicateEmailIsRejected() {
        when(userRepository.existsByEmail("a@b.c")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateUserRequest("A@b.c", "A", "B", "VENDOR", "2019/123456/07")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void adminCannotSelfRegister() {
        assertThatThrownBy(() -> service.create(new CreateUserRequest("a@b.c", "A", "B", "ADMIN", null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(userRepository, never()).save(any());
    }

    @Test
    void vendorWithoutBusinessNumberIsRejected() {
        assertThatThrownBy(() -> service.create(new CreateUserRequest("v@x.y", "V", "V", "VENDOR", " ")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("business registration number");
        verify(userRepository, never()).save(any());
    }

    @Test
    void businessNumberIsOnlyStoredForVendors() {
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(new CreateUserRequest("s@university.com", "S", "S", "STUDENT", "ignored"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getBusinessRegistrationNumber()).isNull();
    }

    @Test
    void verifyingPendingVendorActivatesAndNotifies() {
        User vendor = User.register("v@x.y", "V", "V", UserType.VENDOR, "2019/123456/07");
        vendor.markEmailVerified();
        when(userRepository.findByGuid(vendor.getGuid())).thenReturn(Optional.of(vendor));
        when(roleRepository.findByUserGuid(vendor.getGuid())).thenReturn(List.of());

        UserResponse response = service.verify(vendor.getGuid());

        assertThat(response.verificationStatus()).isEqualTo("VERIFIED");
        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(notificationClient).inbox(eq(vendor.getGuid()), eq("You're verified!"), anyString(), anyString(), anyString());
    }

    @Test
    void adminCanSuspendAnotherUser() {
        User vendor = User.register("v@x.y", "V", "V", UserType.VENDOR, "2019/123456/07");
        vendor.markEmailVerified();
        when(userRepository.findByGuid(vendor.getGuid())).thenReturn(Optional.of(vendor));

        UserResponse response = service.changeStatus("admin", vendor.getGuid(), UserStatus.SUSPENDED, "Repeated low ratings");

        assertThat(response.status()).isEqualTo("SUSPENDED");
        verify(notificationClient).inbox(eq(vendor.getGuid()), eq("Account status changed"), contains("Repeated low ratings"), anyString(), anyString());
    }

    @Test
    void adminCannotChangeOwnStatusOrDropOwnAdminRole() {
        assertThatThrownBy(() -> service.changeStatus("admin", "admin", UserStatus.SUSPENDED, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.updateRoles("admin", "admin", Set.of(Role.STUDENT))).isInstanceOf(ApiException.class);
    }

    @Test
    void cannotSetStatusBackToPending() {
        assertThatThrownBy(() -> service.changeStatus("admin", "u", UserStatus.PENDING, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updatingRolesOnlyAddsAndRemovesTheDifference() {
        User user = User.register("s@university.com", "S", "S", UserType.STUDENT, null);
        UserRole student = new UserRole(user.getGuid(), Role.STUDENT);
        UserRole leader = new UserRole(user.getGuid(), Role.COMMUNITY_LEADER);
        when(userRepository.findByGuid(user.getGuid())).thenReturn(Optional.of(user));
        when(roleRepository.findByUserGuid(user.getGuid())).thenReturn(List.of(student, leader));

        UserResponse response = service.updateRoles("admin", user.getGuid(), Set.of(Role.STUDENT, Role.VENDOR));

        assertThat(response.roles()).containsExactly("STUDENT", "VENDOR");
        verify(roleRepository).delete(leader);
        verify(roleRepository, never()).delete(student);
        ArgumentCaptor<UserRole> added = ArgumentCaptor.forClass(UserRole.class);
        verify(roleRepository).save(added.capture());
        assertThat(added.getValue().getRole()).isEqualTo(Role.VENDOR);
    }

    @Test
    void cannotVerifyUserWhoHasNotVerifiedEmail() {
        User vendor = User.register("v@x.y", "V", "V", UserType.VENDOR, "2019/123456/07");
        when(userRepository.findByGuid(vendor.getGuid())).thenReturn(Optional.of(vendor));

        assertThatThrownBy(() -> service.verify(vendor.getGuid())).isInstanceOf(ApiException.class);
        assertThat(vendor.getVerificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
    }
}

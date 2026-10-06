package community.store.user.service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import community.store.common.client.NotificationClient;
import community.store.common.client.dto.CreateUserRequest;
import community.store.common.client.dto.UserSummaryResponse;
import community.store.common.web.ApiException;
import community.store.common.web.PageResponse;
import community.store.user.domain.Role;
import community.store.user.domain.User;
import community.store.user.domain.UserBankingDetailRepository;
import community.store.user.domain.UserRepository;
import community.store.user.domain.UserRole;
import community.store.user.domain.UserRoleRepository;
import community.store.user.domain.UserStatus;
import community.store.user.domain.UserType;
import community.store.user.domain.VerificationStatus;
import community.store.user.dto.UpdateUserRequest;
import community.store.user.dto.UserResponse;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final UserRoleRepository roleRepository;
    private final UserBankingDetailRepository bankingDetailRepository;
    private final NotificationClient notificationClient;

    public UserService(UserRepository userRepository,
                       UserRoleRepository roleRepository,
                       UserBankingDetailRepository bankingDetailRepository,
                       NotificationClient notificationClient) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.bankingDetailRepository = bankingDetailRepository;
        this.notificationClient = notificationClient;
    }

    // --- self-service ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public UserResponse getMe(String guid) {
        return toResponse(load(guid));
    }

    @Transactional
    public UserResponse updateMe(String guid, UpdateUserRequest request) {
        User user = load(guid);
        user.rename(request.firstName(), request.lastName());
        return toResponse(user);
    }

    // --- backoffice -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(VerificationStatus verificationStatus, UserStatus status, Role role, String query,
                                             Pageable pageable) {
        String prefix = query == null || query.isBlank() ? null : escapeLike(query.trim().toLowerCase(Locale.ROOT)) + "%";
        Page<User> page = userRepository.search(verificationStatus, status, role, prefix, pageable);
        Map<String, List<String>> roles = roleRepository.findByUserGuidIn(page.map(User::getGuid).getContent()).stream()
                .collect(Collectors.groupingBy(UserRole::getUserGuid,
                        Collectors.mapping(r -> r.getRole().name(), Collectors.toList())));
        return PageResponse.from(page, u -> toResponse(u, roles.getOrDefault(u.getGuid(), List.of())));
    }

    @Transactional
    public UserResponse verify(String guid) {
        User user = load(guid);
        if (user.getVerificationStatus() != VerificationStatus.PENDING_REVIEW
                && user.getVerificationStatus() != VerificationStatus.REJECTED) {
            throw ApiException.conflict("User is " + user.getVerificationStatus() + " and cannot be verified");
        }
        user.verify();
        log.info("User {} verified by admin", guid);
        notificationClient.inbox(guid, "You're verified!",
                "Your Community Store account has been verified. You can now use all features for your role.",
                NotificationClient.ENTITY_ACCOUNT, guid);
        return toResponse(user);
    }

    @Transactional
    public UserResponse reject(String guid, String reason) {
        User user = load(guid);
        if (user.getVerificationStatus() != VerificationStatus.PENDING_REVIEW) {
            throw ApiException.conflict("Only users pending review can be rejected");
        }
        user.reject();
        log.info("User {} rejected by admin: {}", guid, reason);
        notificationClient.inbox(guid, "Verification unsuccessful",
                "Your account verification was rejected. Reason: " + reason,
                NotificationClient.ENTITY_ACCOUNT, guid);
        return toResponse(user);
    }

    /**
     * Suspend, deactivate or re-activate an account. Suspended/deactivated users cannot log in or refresh tokens.
     * Admins cannot change their own status (prevents locking the last admin out).
     */
    @Transactional
    public UserResponse changeStatus(String actorGuid, String guid, UserStatus newStatus, String reason) {
        if (actorGuid.equals(guid)) {
            throw ApiException.forbidden("You cannot change the status of your own account");
        }
        if (newStatus == UserStatus.PENDING) {
            throw ApiException.badRequest("Status can only be set to ACTIVE, SUSPENDED or DEACTIVATED");
        }
        User user = load(guid);
        UserStatus previous = user.getStatus();
        if (previous == newStatus) {
            return toResponse(user);
        }
        user.changeStatus(newStatus);
        log.info("Admin {} changed status of user {} from {} to {}", actorGuid, guid, previous, newStatus);
        String why = reason == null || reason.isBlank() ? "" : " Reason: " + reason.trim();
        notificationClient.inbox(guid, "Account status changed",
                "Your account is now " + newStatus.name().toLowerCase(Locale.ROOT) + "." + why,
                NotificationClient.ENTITY_ACCOUNT, guid);
        return toResponse(user);
    }

    /**
     * Replaces a user's role set. Changes apply from the user's next login or token refresh.
     * Admins cannot remove their own ADMIN role.
     */
    @Transactional
    public UserResponse updateRoles(String actorGuid, String guid, Set<Role> requested) {
        if (requested == null || requested.isEmpty()) {
            throw ApiException.badRequest("A user needs at least one role");
        }
        if (actorGuid.equals(guid) && !requested.contains(Role.ADMIN)) {
            throw ApiException.forbidden("You cannot remove your own ADMIN role");
        }
        User user = load(guid);
        List<UserRole> current = roleRepository.findByUserGuid(guid);
        Set<Role> existing = current.stream().map(UserRole::getRole).collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
        // Diff rather than delete-all/insert-all: Hibernate flushes inserts before deletes, which would trip uk(user_guid, role).
        current.stream().filter(r -> !requested.contains(r.getRole())).forEach(roleRepository::delete);
        requested.stream().filter(r -> !existing.contains(r)).forEach(r -> roleRepository.save(new UserRole(guid, r)));
        roleRepository.flush();
        List<String> roles = requested.stream().map(Role::name).sorted().toList();
        log.info("Admin {} changed roles of user {} from {} to {}", actorGuid, guid, existing, roles);
        notificationClient.inbox(guid, "Your roles changed",
                "Your account roles are now: " + String.join(", ", roles) + ". Log in again to see the change.",
                NotificationClient.ENTITY_ACCOUNT, guid);
        return toResponse(user, roles);
    }

    // --- system (service-to-service) ------------------------------------------------------

    /** Guids of every ADMIN that can still log in (used to address admin notifications). */
    @Transactional(readOnly = true)
    public List<String> activeAdminGuids() {
        List<String> guids = roleRepository.findByRole(Role.ADMIN).stream().map(UserRole::getUserGuid).toList();
        return userRepository.findByGuidIn(guids).stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE).map(User::getGuid).toList();
    }

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getSummaries(Collection<String> guids) {
        if (guids.isEmpty()) {
            return List.of();
        }
        return userRepository.findByGuidIn(guids).stream().map(this::toSummary).toList();
    }

    @Transactional
    public UserSummaryResponse create(CreateUserRequest request) {
        UserType userType;
        try {
            userType = UserType.valueOf(request.userType());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unsupported userType: " + request.userType());
        }
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        String businessNumber = userType == UserType.VENDOR ? request.businessRegistrationNumber() : null;
        if (userType == UserType.VENDOR && (businessNumber == null || businessNumber.isBlank())) {
            throw ApiException.badRequest("Vendors must provide a business registration number");
        }
        User user = userRepository.save(User.register(email, request.firstName(), request.lastName(), userType, businessNumber));
        roleRepository.save(new UserRole(user.getGuid(), Role.valueOf(userType.name())));
        log.info("Registered {} user {}", userType, user.getGuid());
        return toSummary(user);
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse getSummary(String guid) {
        return toSummary(load(guid));
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse getSummaryByEmail(String email) {
        return toSummary(userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> ApiException.notFound("User not found")));
    }

    @Transactional
    public UserSummaryResponse markEmailVerified(String guid) {
        User user = load(guid);
        user.markEmailVerified();
        if (user.getUserType() == UserType.STUDENT) {
            log.info("[SIMULATED] University email check passed for student {}", guid);
        }
        log.info("User {} email verified: status={}, verification={}", guid, user.getStatus(), user.getVerificationStatus());
        return toSummary(user);
    }

    // --- mapping --------------------------------------------------------------------------

    User load(String guid) {
        return userRepository.findByGuid(guid).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    List<String> rolesOf(String guid) {
        return roleRepository.findByUserGuid(guid).stream().map(r -> r.getRole().name()).sorted().toList();
    }

    private UserResponse toResponse(User user) {
        return toResponse(user, rolesOf(user.getGuid()));
    }

    private UserResponse toResponse(User user, List<String> roles) {
        return new UserResponse(user.getGuid(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getUserType() == null ? null : user.getUserType().name(), user.getBusinessRegistrationNumber(), roles,
                user.getStatus().name(), user.getVerificationStatus().name(), user.getCreatedAt());
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private UserSummaryResponse toSummary(User user) {
        return new UserSummaryResponse(user.getGuid(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getUserType() == null ? null : user.getUserType().name(),
                user.getStatus().name(), user.getVerificationStatus().name(), rolesOf(user.getGuid()),
                bankingDetailRepository.existsByUserGuid(user.getGuid()));
    }
}

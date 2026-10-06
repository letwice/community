package community.store.user.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "`user`")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guid", nullable = false, unique = true, length = 36, updatable = false)
    private String guid;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    /** Null for seeded ADMIN accounts (admin is a role, not a registration type). */
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type")
    private UserType userType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VerificationStatus verificationStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Vendors only: business registration number captured at sign-up for admin review. */
    @Column(name = "business_registration_number", length = 50)
    private String businessRegistrationNumber;

    @Version
    @Column(name = "version")
    private Integer version;

    protected User() {
    }

    public static User register(String email, String firstName, String lastName, UserType userType,
                                String businessRegistrationNumber) {
        User user = new User();
        user.guid = UUID.randomUUID().toString();
        user.email = email;
        user.firstName = firstName;
        user.lastName = lastName;
        user.userType = userType;
        user.businessRegistrationNumber = businessRegistrationNumber;
        user.status = UserStatus.PENDING;
        user.verificationStatus = VerificationStatus.UNVERIFIED;
        return user;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Mocked email verification. Students are verified immediately (university email is assumed valid);
     * vendors, community leaders and faculty move into the admin review queue.
     */
    public void markEmailVerified() {
        if (status == UserStatus.PENDING) {
            status = UserStatus.ACTIVE;
        }
        if (verificationStatus == VerificationStatus.UNVERIFIED) {
            verificationStatus = userType == UserType.STUDENT ? VerificationStatus.VERIFIED : VerificationStatus.PENDING_REVIEW;
        }
    }

    public void verify() {
        verificationStatus = VerificationStatus.VERIFIED;
        status = UserStatus.ACTIVE;
    }

    public void reject() {
        verificationStatus = VerificationStatus.REJECTED;
    }

    public void changeStatus(UserStatus newStatus) {
        this.status = newStatus;
    }

    public void rename(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public Long getId() {
        return id;
    }

    public String getGuid() {
        return guid;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public UserType getUserType() {
        return userType;
    }

    public String getBusinessRegistrationNumber() {
        return businessRegistrationNumber;
    }

    public UserStatus getStatus() {
        return status;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

package community.store.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import community.store.user.config.AccountNumberConverter;

@Entity
@Table(name = "user_banking_detail")
public class UserBankingDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_guid", nullable = false, unique = true, length = 36)
    private String userGuid;

    @Column(name = "account_holder_name", length = 150)
    private String accountHolderName;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Convert(converter = AccountNumberConverter.class)
    @Column(name = "account_number_encrypted")
    private String accountNumber;

    @Column(name = "branch_code", length = 20)
    private String branchCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type")
    private AccountType accountType;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    protected UserBankingDetail() {
    }

    public UserBankingDetail(String userGuid) {
        this.userGuid = userGuid;
    }

    /** Any change to the payout destination resets verification. */
    public void update(String accountHolderName, String bankName, String accountNumber, String branchCode, AccountType accountType) {
        this.accountHolderName = accountHolderName;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.branchCode = branchCode;
        this.accountType = accountType;
        this.verified = false;
    }

    public String getUserGuid() {
        return userGuid;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public String getBankName() {
        return bankName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public boolean isVerified() {
        return verified;
    }
}

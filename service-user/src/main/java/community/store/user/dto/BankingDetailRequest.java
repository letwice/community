package community.store.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import community.store.user.domain.AccountType;

public record BankingDetailRequest(
        @NotBlank @Size(max = 150) String accountHolderName,
        @NotBlank @Size(max = 100) String bankName,
        @NotBlank @Pattern(regexp = "\\d{6,20}", message = "must be 6-20 digits") String accountNumber,
        @NotBlank @Size(max = 20) String branchCode,
        @NotNull AccountType accountType) {
}

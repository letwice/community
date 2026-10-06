package community.store.user.dto;

/**
 * Account number is only ever returned masked (last four digits).
 */
public record BankingDetailResponse(
        String accountHolderName,
        String bankName,
        String maskedAccountNumber,
        String branchCode,
        String accountType,
        boolean verified) {
}

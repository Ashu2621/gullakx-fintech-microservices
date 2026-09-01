package com.gullakx.wallet.web;

import jakarta.validation.constraints.*;

public final class WalletDtos {

    private WalletDtos() {
    }

    public record OpenWalletRequest(
            @NotBlank @Size(max = 64) String ownerId,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @PositiveOrZero long openingBalanceMinor) {
    }

    public record WalletResponse(Long id, String ownerId, String currency, long balanceMinor) {
    }

    public record TransferRequest(
            @NotNull Long sourceWalletId,
            @NotNull Long destWalletId,
            @Positive long amountMinor) {
    }

    public record TransferResponse(
            Long transferId,
            String status,
            String failureReason,
            long amountMinor,
            boolean replayed) {
    }

    public record LedgerEntryResponse(
            Long id, Long transferId, String direction, long amountMinor, long balanceAfter) {
    }
}

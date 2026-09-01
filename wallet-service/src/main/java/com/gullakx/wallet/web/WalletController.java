package com.gullakx.wallet.web;

import com.gullakx.common.dto.ApiResponse;
import com.gullakx.wallet.domain.LedgerEntry;
import com.gullakx.wallet.domain.Transfer;
import com.gullakx.wallet.domain.TransferStatus;
import com.gullakx.wallet.domain.Wallet;
import com.gullakx.wallet.service.TransferService;
import com.gullakx.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.gullakx.wallet.web.WalletDtos.*;

/**
 * Every endpoint answers two separate questions before doing anything: who is
 * calling — the filter established that — and whether that caller is entitled to
 * this particular wallet, which is checked here because it depends on which
 * wallet was named.
 *
 * Treating a verified token as proof of entitlement is the mistake this
 * separation exists to prevent. Before it, any authenticated user could name any
 * wallet id and move the money out of it.
 */
@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletService walletService;
    private final TransferService transferService;

    public WalletController(WalletService walletService, TransferService transferService) {
        this.walletService = walletService;
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WalletResponse>> open(
            @AuthenticationPrincipal Long callerId,
            @Valid @RequestBody OpenWalletRequest request) {

        // Ownership comes from the token, never from the body.
        Wallet wallet = walletService.openWallet(
                String.valueOf(callerId), request.currency().toUpperCase(), request.openingBalanceMinor());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toResponse(wallet), "Wallet ready"));
    }

    @GetMapping("/{id}")
    public ApiResponse<WalletResponse> get(@AuthenticationPrincipal Long callerId, @PathVariable Long id) {
        return ApiResponse.success(toResponse(walletService.getOwned(id, callerId)), "OK");
    }

    /**
     * The idempotency key travels as a header, not in the body.
     *
     * It describes the delivery of the request rather than the transfer itself,
     * so a client can retry a byte-identical payload — which is what an HTTP
     * library does automatically on a timeout, without asking the application.
     */
    @PostMapping("/transfers")
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(
            @AuthenticationPrincipal Long callerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        // Only the source is checked. Receiving money needs no permission from
        // the recipient, and requiring it would make paying a stranger
        // impossible.
        if (!walletService.isOwnedBy(request.sourceWalletId(), callerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("NOT_YOUR_WALLET", "You do not own the source wallet"));
        }

        TransferService.Result result = transferService.transfer(new TransferService.Command(
                idempotencyKey, request.sourceWalletId(), request.destWalletId(), request.amountMinor()));

        Transfer transfer = result.transfer();
        TransferResponse body = new TransferResponse(
                transfer.getId(),
                transfer.getStatus().name(),
                transfer.getFailureReason(),
                transfer.getAmountMinor(),
                result.replayed());

        // A rejected transfer is a business outcome, not a transport failure:
        // 422 rather than 400, because the request was well-formed and was
        // understood — it simply could not be carried out.
        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            return ResponseEntity.ok(
                    ApiResponse.success(body, result.replayed() ? "Replayed" : "Transfer completed"));
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(transfer.getFailureReason(), "Transfer rejected"));
    }

    @GetMapping("/{id}/statement")
    public ApiResponse<List<LedgerEntryResponse>> statement(
            @AuthenticationPrincipal Long callerId, @PathVariable Long id) {

        // A statement is a complete record of someone's money. Read access here
        // is exactly as sensitive as write access.
        walletService.getOwned(id, callerId);

        List<LedgerEntryResponse> entries = transferService.statement(id).stream()
                .map(WalletController::toResponse)
                .toList();
        return ApiResponse.success(entries, "OK");
    }

    private static WalletResponse toResponse(Wallet w) {
        return new WalletResponse(w.getId(), w.getOwnerId(), w.getCurrency(), w.getBalanceMinor());
    }

    private static LedgerEntryResponse toResponse(LedgerEntry e) {
        return new LedgerEntryResponse(
                e.getId(), e.getTransferId(), e.getDirection().name(), e.getAmountMinor(), e.getBalanceAfter());
    }
}

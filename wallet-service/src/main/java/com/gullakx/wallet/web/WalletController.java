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
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.gullakx.wallet.web.WalletDtos.*;

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
    public ResponseEntity<ApiResponse<WalletResponse>> open(@Valid @RequestBody OpenWalletRequest request) {
        Wallet wallet = walletService.openWallet(
                request.ownerId(), request.currency().toUpperCase(), request.openingBalanceMinor());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toResponse(wallet), "Wallet ready"));
    }

    @GetMapping("/{id}")
    public ApiResponse<WalletResponse> get(@PathVariable Long id) {
        return ApiResponse.success(toResponse(walletService.get(id)), "OK");
    }

    /**
     * The idempotency key travels as a header, not in the body.
     *
     * It describes the delivery of the request rather than the transfer itself,
     * and keeping it out of the body means a client can retry a byte-identical
     * payload — which is what an HTTP client library does automatically on a
     * timeout, without asking the application.
     */
    @PostMapping("/transfers")
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

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
        HttpStatus status = transfer.getStatus() == TransferStatus.COMPLETED
                ? HttpStatus.OK
                : HttpStatus.UNPROCESSABLE_ENTITY;

        return ResponseEntity.status(status).body(
                transfer.getStatus() == TransferStatus.COMPLETED
                        ? ApiResponse.success(body, result.replayed() ? "Replayed" : "Transfer completed")
                        : ApiResponse.error(transfer.getFailureReason(), "Transfer rejected"));
    }

    @GetMapping("/{id}/statement")
    public ApiResponse<List<LedgerEntryResponse>> statement(@PathVariable Long id) {
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

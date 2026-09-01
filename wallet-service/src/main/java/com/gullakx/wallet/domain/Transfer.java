package com.gullakx.wallet.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 128, unique = true)
    private String idempotencyKey;

    @Column(name = "source_wallet_id", nullable = false)
    private Long sourceWalletId;

    @Column(name = "dest_wallet_id", nullable = false)
    private Long destWalletId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransferStatus status;

    /**
     * Why a rejected transfer is still written.
     *
     * The obvious implementation returns an error and stores nothing. That makes
     * the second attempt with the same idempotency key look brand new, so a
     * client retrying after a rejection gets a second evaluation — and if the
     * balance moved in between, two identical requests produce two different
     * outcomes. Recording the rejection makes the answer stable and gives
     * support a record of what was attempted.
     */
    @Column(name = "failure_reason", length = 64)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Transfer() {
    }

    private Transfer(String idempotencyKey, Long sourceWalletId, Long destWalletId,
                     long amountMinor, TransferStatus status, String failureReason) {
        this.idempotencyKey = idempotencyKey;
        this.sourceWalletId = sourceWalletId;
        this.destWalletId = destWalletId;
        this.amountMinor = amountMinor;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = Instant.now();
    }

    public static Transfer completed(String key, Long source, Long dest, long amountMinor) {
        return new Transfer(key, source, dest, amountMinor, TransferStatus.COMPLETED, null);
    }

    public static Transfer rejected(String key, Long source, Long dest, long amountMinor, String reason) {
        return new Transfer(key, source, dest, amountMinor, TransferStatus.REJECTED, reason);
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getSourceWalletId() {
        return sourceWalletId;
    }

    public Long getDestWalletId() {
        return destWalletId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

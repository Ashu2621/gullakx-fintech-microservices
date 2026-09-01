package com.gullakx.wallet.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One side of a double-entry pair. A completed transfer writes exactly two:
 * a DEBIT on the source and a CREDIT on the destination, of equal amount.
 *
 * There is no update or delete path. The ledger is append-only, because an
 * edited history is not a history — a correction is a new compensating pair,
 * which leaves both the original and the fix visible.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_id", nullable = false)
    private Long transferId;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Direction direction;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(Long transferId, Long walletId, Direction direction,
                       long amountMinor, long balanceAfter) {
        this.transferId = transferId;
        this.walletId = walletId;
        this.direction = direction;
        this.amountMinor = amountMinor;
        this.balanceAfter = balanceAfter;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTransferId() {
        return transferId;
    }

    public Long getWalletId() {
        return walletId;
    }

    public Direction getDirection() {
        return direction;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    /** Signed contribution to a balance: credits add, debits subtract. */
    public long signedAmount() {
        return direction == Direction.CREDIT ? amountMinor : -amountMinor;
    }
}

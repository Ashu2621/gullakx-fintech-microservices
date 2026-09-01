package com.gullakx.statement.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "statement_entries",
       uniqueConstraints = @UniqueConstraint(name = "uq_statement_entry",
                                             columnNames = {"wallet_id", "transfer_id"}))
public class StatementEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id", nullable = false)
    private long walletId;

    @Column(name = "transfer_id", nullable = false)
    private long transferId;

    @Column(nullable = false, length = 6)
    private String direction;

    /** The other wallet in the transfer, from this wallet's point of view. */
    @Column(nullable = false)
    private long counterparty;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    /** When the transfer committed — not when this row was written. */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected StatementEntry() {
    }

    public StatementEntry(long walletId, long transferId, String direction, long counterparty,
                          long amountMinor, String currency, Instant occurredAt) {
        this.walletId = walletId;
        this.transferId = transferId;
        this.direction = direction;
        this.counterparty = counterparty;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.occurredAt = occurredAt;
        this.recordedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public long getWalletId() {
        return walletId;
    }

    public long getTransferId() {
        return transferId;
    }

    public String getDirection() {
        return direction;
    }

    public long getCounterparty() {
        return counterparty;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    /** Signed contribution to a running balance. */
    public long signedAmount() {
        return "CREDIT".equals(direction) ? amountMinor : -amountMinor;
    }
}

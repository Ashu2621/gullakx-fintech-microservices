package com.gullakx.wallet.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;

    @Column(nullable = false, length = 3)
    private String currency;

    /** Minor units (paise). Never a floating-point type — see V1__wallet_schema.sql. */
    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    /**
     * True only for the per-currency funding account. See the schema comment:
     * this is the one wallet allowed to hold a negative balance, and that
     * negative number is how much customer money the system is holding.
     */
    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Wallet() {
    }

    /**
     * Wallets always open empty. Money arrives only through a transfer, so
     * every rupee in a balance has a ledger entry explaining it.
     */
    public Wallet(String ownerId, String currency) {
        this(ownerId, currency, false);
    }

    private Wallet(String ownerId, String currency, boolean system) {
        this.ownerId = ownerId;
        this.currency = currency;
        this.balanceMinor = 0;
        this.system = system;
        this.createdAt = Instant.now();
    }

    public static Wallet fundingAccount(String currency) {
        return new Wallet(SYSTEM_OWNER, currency, true);
    }

    public static final String SYSTEM_OWNER = "__system__";

    public Long getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getCurrency() {
        return currency;
    }

    public long getBalanceMinor() {
        return balanceMinor;
    }

    public boolean isSystem() {
        return system;
    }

    /** Whether this wallet can cover a debit of the given size. */
    public boolean canFund(long amountMinor) {
        return system || balanceMinor >= amountMinor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Balance mutation is deliberately not a public setter.
     *
     * Callers must go through debit/credit, which keep the sign rules in one
     * place. A setter would let any future code path assign a balance without
     * writing the matching ledger entry, and a balance with no ledger behind it
     * is exactly the state this design exists to make impossible.
     */
    public void debit(long amountMinor) {
        requirePositive(amountMinor);
        if (!canFund(amountMinor)) {
            throw new IllegalStateException("Insufficient funds");
        }
        balanceMinor -= amountMinor;
    }

    public void credit(long amountMinor) {
        requirePositive(amountMinor);
        balanceMinor += amountMinor;
    }

    private static void requirePositive(long amountMinor) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}

package com.gullakx.wallet.service;

import com.gullakx.wallet.domain.Wallet;
import com.gullakx.wallet.repository.WalletRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository wallets;
    private final TransferService transfers;

    // @Lazy breaks the constructor cycle between the two services. They call
    // each other by design: opening a funded wallet is a transfer, and a
    // transfer needs wallets to exist.
    public WalletService(WalletRepository wallets, @Lazy TransferService transfers) {
        this.wallets = wallets;
        this.transfers = transfers;
    }

    /**
     * Idempotent by (owner, currency): calling twice returns the existing wallet
     * rather than creating a second one. The UNIQUE constraint backs this up, so
     * a concurrent double-create fails at the database instead of splitting a
     * customer's money across two wallets.
     *
     * An opening balance is funded by a real transfer from the currency's
     * funding account, never by writing a number onto the new row. That costs an
     * extra transfer, and buys the property the whole design rests on: every
     * balance is explained by ledger entries, so reconciliation is a check
     * rather than a wish.
     */
    @Transactional
    public Wallet openWallet(String ownerId, String currency, long openingBalanceMinor) {
        if (openingBalanceMinor < 0) {
            throw new IllegalArgumentException("OPENING_BALANCE_NEGATIVE");
        }
        if (Wallet.SYSTEM_OWNER.equals(ownerId)) {
            throw new IllegalArgumentException("RESERVED_OWNER_ID");
        }

        Wallet wallet = wallets.findByOwnerIdAndCurrency(ownerId, currency)
                .orElseGet(() -> wallets.save(new Wallet(ownerId, currency)));

        if (openingBalanceMinor > 0) {
            Wallet funding = fundingAccount(currency);
            transfers.transfer(new TransferService.Command(
                    "open:" + wallet.getId() + ":" + UUID.randomUUID(),
                    funding.getId(), wallet.getId(), openingBalanceMinor));
        }

        return wallets.findById(wallet.getId()).orElseThrow();
    }

    /** Created on first use, one per currency. */
    @Transactional
    public Wallet fundingAccount(String currency) {
        return wallets.findByOwnerIdAndCurrency(Wallet.SYSTEM_OWNER, currency)
                .orElseGet(() -> wallets.save(Wallet.fundingAccount(currency)));
    }

    @Transactional(readOnly = true)
    public Wallet get(Long id) {
        return wallets.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("WALLET_NOT_FOUND"));
    }

    /**
     * Fetch a wallet only if the caller owns it.
     *
     * A missing wallet and someone else's wallet return the same error on
     * purpose. Distinguishing them turns this endpoint into a way to probe
     * which wallet ids exist, which is a small leak that costs nothing to
     * close.
     */
    @Transactional(readOnly = true)
    public Wallet getOwned(Long id, Long callerId) {
        return wallets.findById(id)
                .filter(w -> w.getOwnerId().equals(String.valueOf(callerId)))
                .orElseThrow(() -> new IllegalArgumentException("WALLET_NOT_FOUND"));
    }

    /** True when the wallet exists and belongs to this caller. */
    @Transactional(readOnly = true)
    public boolean isOwnedBy(Long walletId, Long callerId) {
        return wallets.findById(walletId)
                .map(w -> w.getOwnerId().equals(String.valueOf(callerId)))
                .orElse(false);
    }
}

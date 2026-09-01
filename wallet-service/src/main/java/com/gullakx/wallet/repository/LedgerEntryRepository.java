package com.gullakx.wallet.repository;

import com.gullakx.wallet.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByWalletIdOrderByIdAsc(Long walletId);

    List<LedgerEntry> findByTransferId(Long transferId);

    /**
     * Balance derived from the ledger rather than read from the wallet row.
     *
     * Used by reconciliation, not by the transfer path. The cached balance on
     * `wallets` is what transfers read because it is O(1); this is the check
     * that the cache still tells the truth.
     */
    @Query("""
           SELECT COALESCE(SUM(CASE WHEN e.direction = com.gullakx.wallet.domain.Direction.CREDIT
                                    THEN e.amountMinor ELSE -e.amountMinor END), 0)
           FROM LedgerEntry e WHERE e.walletId = :walletId
           """)
    long derivedBalance(@Param("walletId") Long walletId);

    /**
     * Net movement across the entire ledger. Must always be zero: every
     * transfer writes one debit and one matching credit, so the system as a
     * whole neither creates nor destroys money. A non-zero result means a
     * single-sided write got in somewhere.
     */
    @Query("""
           SELECT COALESCE(SUM(CASE WHEN e.direction = com.gullakx.wallet.domain.Direction.CREDIT
                                    THEN e.amountMinor ELSE -e.amountMinor END), 0)
           FROM LedgerEntry e
           """)
    long netAcrossAllWallets();
}

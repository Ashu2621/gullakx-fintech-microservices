package com.gullakx.wallet.repository;

import com.gullakx.wallet.domain.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByOwnerIdAndCurrency(String ownerId, String currency);

    /**
     * Fetch wallets with a write lock (SELECT ... FOR UPDATE).
     *
     * This is the control that makes concurrent transfers safe. Without it, two
     * requests can both read a balance of 100, both decide a debit of 100 is
     * affordable, and both write — leaving -100 or a lost update depending on
     * which commits last. Read-then-write on shared state is not safe unless
     * the read takes the lock.
     *
     * Ordering by id is not cosmetic: see TransferService for why the two
     * wallets in a transfer must always be locked in the same order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id IN :ids ORDER BY w.id")
    List<Wallet> lockAllById(@Param("ids") List<Long> ids);
}

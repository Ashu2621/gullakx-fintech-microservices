package com.gullakx.statement.repository;

import com.gullakx.statement.domain.StatementEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatementEntryRepository extends JpaRepository<StatementEntry, Long> {

    List<StatementEntry> findByWalletIdOrderByOccurredAtDescIdDesc(long walletId);

    boolean existsByWalletIdAndTransferId(long walletId, long transferId);

    long countByTransferId(long transferId);
}

package com.gullakx.wallet;

import com.gullakx.wallet.domain.Direction;
import com.gullakx.wallet.domain.LedgerEntry;
import com.gullakx.wallet.domain.Transfer;
import com.gullakx.wallet.domain.TransferStatus;
import com.gullakx.wallet.domain.Wallet;
import com.gullakx.wallet.repository.LedgerEntryRepository;
import com.gullakx.wallet.service.TransferService;
import com.gullakx.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * The invariants that make this a ledger rather than two numbers being edited.
 */
@SpringBootTest
@ActiveProfiles("test")
class LedgerInvariantTest {

    @Autowired
    private WalletService walletService;
    @Autowired
    private TransferService transferService;
    @Autowired
    private LedgerEntryRepository ledger;

    private Long alice;
    private Long bob;

    @BeforeEach
    void openWallets() {
        alice = walletService.openWallet("alice-" + System.nanoTime(), "INR", 100_00).getId();
        bob = walletService.openWallet("bob-" + System.nanoTime(), "INR", 0).getId();
    }

    @Test
    @DisplayName("a completed transfer writes exactly two entries that cancel out")
    void doubleEntry() {
        var result = transferService.transfer(
                new TransferService.Command("t-" + System.nanoTime(), alice, bob, 30_00));

        assertThat(result.transfer().getStatus()).isEqualTo(TransferStatus.COMPLETED);

        List<LedgerEntry> entries = ledger.findByTransferId(result.transfer().getId());
        assertThat(entries).hasSize(2);
        assertThat(entries.stream().mapToLong(LedgerEntry::signedAmount).sum())
                .as("debit and credit must cancel")
                .isZero();
        assertThat(entries).extracting(LedgerEntry::getDirection)
                .containsExactlyInAnyOrder(Direction.DEBIT, Direction.CREDIT);
    }

    @Test
    @DisplayName("money is neither created nor destroyed across the whole ledger")
    void conservation() {
        for (int i = 0; i < 5; i++) {
            transferService.transfer(new TransferService.Command("c-" + i + "-" + System.nanoTime(), alice, bob, 10_00));
        }
        assertThat(transferService.netAcrossAllWallets())
                .as("system-wide net movement")
                .isZero();
    }

    @Test
    @DisplayName("the cached balance always agrees with the ledger")
    void reconciliation() {
        transferService.transfer(new TransferService.Command("r1-" + System.nanoTime(), alice, bob, 25_00));
        transferService.transfer(new TransferService.Command("r2-" + System.nanoTime(), bob, alice, 5_00));

        assertThat(transferService.reconciles(alice)).isTrue();
        assertThat(transferService.reconciles(bob)).isTrue();
        assertThat(ledger.derivedBalance(alice)).isEqualTo(100_00 - 25_00 + 5_00);
        assertThat(ledger.derivedBalance(bob)).isEqualTo(25_00 - 5_00);
    }

    @Test
    @DisplayName("each entry records the balance as it stood immediately after")
    void balanceAfterIsSnapshotted() {
        transferService.transfer(new TransferService.Command("s1-" + System.nanoTime(), alice, bob, 40_00));
        transferService.transfer(new TransferService.Command("s2-" + System.nanoTime(), alice, bob, 10_00));

        // Three entries, not two: the first is the opening balance arriving from
        // the funding account. That entry existing is the point -- a balance
        // with no ledger row behind it would mean the ledger is not the truth.
        List<LedgerEntry> aliceEntries = transferService.statement(alice);
        assertThat(aliceEntries).extracting(LedgerEntry::getBalanceAfter)
                .containsExactly(100_00L, 60_00L, 50_00L);
        assertThat(aliceEntries).extracting(LedgerEntry::getDirection)
                .containsExactly(Direction.CREDIT, Direction.DEBIT, Direction.DEBIT);
    }

    @Test
    @DisplayName("an overdraft is refused and leaves no trace in the ledger")
    void overdraftRefused() {
        long before = ledger.findByWalletIdOrderByIdAsc(alice).size();

        var result = transferService.transfer(
                new TransferService.Command("o-" + System.nanoTime(), alice, bob, 500_00));

        assertThat(result.transfer().getStatus()).isEqualTo(TransferStatus.REJECTED);
        assertThat(result.transfer().getFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");
        // A rejection is recorded as a transfer, but writes no ledger entries.
        assertThat(ledger.findByWalletIdOrderByIdAsc(alice)).hasSize((int) before);
        assertThat(ledger.findByTransferId(result.transfer().getId())).isEmpty();
    }

    @Test
    @DisplayName("a transfer between different currencies is refused")
    void currencyMismatch() {
        Long usd = walletService.openWallet("carol-" + System.nanoTime(), "USD", 50_00).getId();
        var result = transferService.transfer(
                new TransferService.Command("cm-" + System.nanoTime(), alice, usd, 10_00));

        assertThat(result.transfer().getStatus()).isEqualTo(TransferStatus.REJECTED);
        assertThat(result.transfer().getFailureReason()).isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    @DisplayName("malformed commands are rejected before anything is written")
    void validation() {
        assertThatThrownBy(() -> transferService.transfer(
                new TransferService.Command("", alice, bob, 10_00)))
                .hasMessageContaining("IDEMPOTENCY_KEY_REQUIRED");

        assertThatThrownBy(() -> transferService.transfer(
                new TransferService.Command("v1-" + System.nanoTime(), alice, bob, 0)))
                .hasMessageContaining("AMOUNT_MUST_BE_POSITIVE");

        assertThatThrownBy(() -> transferService.transfer(
                new TransferService.Command("v2-" + System.nanoTime(), alice, alice, 10_00)))
                .hasMessageContaining("SELF_TRANSFER_NOT_ALLOWED");
    }
}

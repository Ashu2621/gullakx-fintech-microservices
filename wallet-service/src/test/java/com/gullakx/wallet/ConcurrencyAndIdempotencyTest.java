package com.gullakx.wallet;

import com.gullakx.wallet.domain.TransferStatus;
import com.gullakx.wallet.service.TransferService;
import com.gullakx.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tests this project exists for.
 *
 * Everything else is CRUD. These are the two properties that separate a wallet
 * that works from a wallet that works until two people press the button at the
 * same time, or until a phone on a bad connection retries a payment.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyAndIdempotencyTest {

    @Autowired
    private WalletService walletService;
    @Autowired
    private TransferService transferService;

    private Long alice;
    private Long bob;

    @BeforeEach
    void openWallets() {
        long stamp = System.nanoTime();
        alice = walletService.openWallet("alice-" + stamp, "INR", 100_00).getId();
        bob = walletService.openWallet("bob-" + stamp, "INR", 0).getId();
    }

    // ── Idempotency ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("the same key twice moves money once")
    void replayIsNotReExecution() {
        String key = "idem-" + System.nanoTime();

        var first = transferService.transfer(new TransferService.Command(key, alice, bob, 30_00));
        var second = transferService.transfer(new TransferService.Command(key, alice, bob, 30_00));

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.transfer().getId()).isEqualTo(first.transfer().getId());

        assertThat(walletService.get(alice).getBalanceMinor()).isEqualTo(70_00);
        assertThat(walletService.get(bob).getBalanceMinor()).isEqualTo(30_00);
    }

    @Test
    @DisplayName("a rejection is also remembered, so a retry gets the same answer")
    void rejectionIsIdempotentToo() {
        String key = "idem-reject-" + System.nanoTime();

        var first = transferService.transfer(new TransferService.Command(key, alice, bob, 500_00));
        assertThat(first.transfer().getStatus()).isEqualTo(TransferStatus.REJECTED);

        // Fund the wallet so the request would now succeed on its merits...
        walletService.openWallet(walletService.get(alice).getOwnerId(), "INR", 0);
        transferService.transfer(new TransferService.Command("top-up-" + System.nanoTime(), bob, alice, 0 + 1));

        // ...and retry the original key. It must still return the original
        // rejection. Otherwise the same request produces different outcomes
        // depending on when it is retried, which is exactly what an idempotency
        // key is supposed to prevent.
        var retry = transferService.transfer(new TransferService.Command(key, alice, bob, 500_00));
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.transfer().getId()).isEqualTo(first.transfer().getId());
        assertThat(retry.transfer().getStatus()).isEqualTo(TransferStatus.REJECTED);
    }

    @Test
    @DisplayName("concurrent retries of one key still move money once")
    void concurrentRetriesOfSameKey() throws Exception {
        String key = "race-" + System.nanoTime();
        int threads = 8;

        List<TransferService.Result> results = runConcurrently(threads, () ->
                transferService.transfer(new TransferService.Command(key, alice, bob, 10_00)));

        // Every thread got an answer, and they all describe the same transfer.
        assertThat(results).hasSize(threads);
        assertThat(results).extracting(r -> r.transfer().getId()).containsOnly(results.get(0).transfer().getId());

        // The database moved the money exactly once. This is the assertion the
        // application-level "have I seen this key?" check cannot make on its
        // own -- all eight threads can pass that check before any of them
        // commits. The UNIQUE constraint is what makes it true.
        assertThat(walletService.get(alice).getBalanceMinor()).isEqualTo(90_00);
        assertThat(walletService.get(bob).getBalanceMinor()).isEqualTo(10_00);
    }

    // ── Concurrency ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("twenty threads cannot spend a balance that covers ten")
    void noOverdraftUnderContention() throws Exception {
        int threads = 20;
        long amount = 10_00;          // wallet holds 100_00, so ten can succeed

        List<TransferService.Result> results = runConcurrently(threads, () ->
                transferService.transfer(new TransferService.Command(
                        "burst-" + Thread.currentThread().threadId() + "-" + System.nanoTime(),
                        alice, bob, amount)));

        long completed = results.stream()
                .filter(r -> r.transfer().getStatus() == TransferStatus.COMPLETED).count();
        long rejected = results.stream()
                .filter(r -> r.transfer().getStatus() == TransferStatus.REJECTED).count();

        assertThat(completed).as("exactly the affordable number succeed").isEqualTo(10);
        assertThat(rejected).isEqualTo(10);

        assertThat(walletService.get(alice).getBalanceMinor())
                .as("never negative")
                .isZero();
        assertThat(walletService.get(bob).getBalanceMinor()).isEqualTo(100_00);

        // And the ledger still explains both balances.
        assertThat(transferService.reconciles(alice)).isTrue();
        assertThat(transferService.reconciles(bob)).isTrue();
        assertThat(transferService.netAcrossAllWallets()).isZero();
    }

    @Test
    @DisplayName("transfers in opposite directions do not deadlock")
    void oppositeDirectionsDoNotDeadlock() throws Exception {
        // The classic deadlock: A->B locks A then B while B->A locks B then A,
        // and neither can finish. TransferService locks in ascending id order
        // regardless of direction, so the cycle cannot form. Without that
        // ordering this test hangs until the database kills one side.
        transferService.transfer(new TransferService.Command("seed-" + System.nanoTime(), alice, bob, 50_00));

        int pairs = 10;
        List<Callable<TransferService.Result>> work = new ArrayList<>();
        for (int i = 0; i < pairs; i++) {
            final int n = i;
            work.add(() -> transferService.transfer(new TransferService.Command(
                    "ab-" + n + "-" + System.nanoTime(), alice, bob, 1_00)));
            work.add(() -> transferService.transfer(new TransferService.Command(
                    "ba-" + n + "-" + System.nanoTime(), bob, alice, 1_00)));
        }

        List<TransferService.Result> results = invokeAll(work);

        assertThat(results).hasSize(pairs * 2);
        assertThat(results).allMatch(r -> r.transfer().getStatus() == TransferStatus.COMPLETED);
        // Equal traffic in both directions nets to no change.
        assertThat(walletService.get(alice).getBalanceMinor()).isEqualTo(50_00);
        assertThat(walletService.get(bob).getBalanceMinor()).isEqualTo(50_00);
        assertThat(transferService.netAcrossAllWallets()).isZero();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Run one task on N threads, released simultaneously by a latch. */
    private <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<T>> work = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            work.add(() -> {
                // Starting the threads is not the same as starting them at the
                // same time; without the latch they queue up naturally and the
                // race being tested never happens.
                start.await(5, TimeUnit.SECONDS);
                return task.call();
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> c : work) futures.add(pool.submit(c));
            start.countDown();
            return collect(futures);
        } finally {
            pool.shutdownNow();
        }
    }

    private <T> List<T> invokeAll(List<Callable<T>> work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(16, work.size()));
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> c : work) futures.add(pool.submit(c));
            return collect(futures);
        } finally {
            pool.shutdownNow();
        }
    }

    private <T> List<T> collect(List<Future<T>> futures) throws Exception {
        List<T> out = new ArrayList<>();
        AtomicInteger failures = new AtomicInteger();
        for (Future<T> f : futures) {
            try {
                out.add(f.get(30, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                failures.incrementAndGet();
                throw new AssertionError("A concurrent transfer threw: " + e.getCause(), e.getCause());
            }
        }
        assertThat(failures.get()).isZero();
        return out;
    }
}

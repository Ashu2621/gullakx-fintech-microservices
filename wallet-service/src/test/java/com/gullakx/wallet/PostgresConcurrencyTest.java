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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The concurrency claims, re-run against a real PostgreSQL.
 *
 * The fast suite runs on H2 so that `mvn verify` needs no Docker daemon, and
 * that is a genuine trade rather than a free one: H2 honours
 * `SELECT … FOR UPDATE` and the CHECK constraints, but its lock-timeout and
 * deadlock-detection behaviour is its own. A ledger whose correctness rests on
 * row locking should be proven on the engine that actually ships.
 *
 * `disabledWithoutDocker` makes the absence of a daemon a visible skip rather
 * than a silent pass — a test that quietly does nothing when infrastructure is
 * missing is worse than no test, because the green tick still appears.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PostgresConcurrencyTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("gullakx_wallet")
            .withUsername("gullakx")
            .withPassword("gullakx_secret");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

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

    @Test
    @DisplayName("the migration applies cleanly to PostgreSQL")
    void migrationApplies() {
        // H2 accepted a partial index that PostgreSQL would have taken and
        // vice-versa; running Flyway here is what proves the migration is valid
        // on the engine it was written for.
        assertThat(walletService.get(alice).getBalanceMinor()).isEqualTo(100_00);
    }

    @Test
    @DisplayName("twenty threads cannot spend a balance that covers ten")
    void noOverdraftUnderRealRowLocks() throws Exception {
        int threads = 20;
        long amount = 10_00;

        List<TransferService.Result> results = runConcurrently(threads, () ->
                transferService.transfer(new TransferService.Command(
                        "pg-burst-" + Thread.currentThread().threadId() + "-" + System.nanoTime(),
                        alice, bob, amount)));

        long completed = results.stream()
                .filter(r -> r.transfer().getStatus() == TransferStatus.COMPLETED).count();

        assertThat(completed).isEqualTo(10);
        assertThat(walletService.get(alice).getBalanceMinor()).isZero();
        assertThat(walletService.get(bob).getBalanceMinor()).isEqualTo(100_00);
        assertThat(transferService.reconciles(alice)).isTrue();
        assertThat(transferService.netAcrossAllWallets()).isZero();
    }

    @Test
    @DisplayName("opposite-direction transfers do not deadlock on PostgreSQL")
    void noDeadlockOnRealEngine() throws Exception {
        // PostgreSQL detects deadlocks and kills a transaction outright, which
        // H2 does differently. If the ascending-id lock ordering were wrong,
        // this is where it would show up as a killed transfer rather than a
        // slow one.
        transferService.transfer(new TransferService.Command("pg-seed-" + System.nanoTime(), alice, bob, 50_00));

        List<Callable<TransferService.Result>> work = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int n = i;
            work.add(() -> transferService.transfer(new TransferService.Command(
                    "pg-ab-" + n + "-" + System.nanoTime(), alice, bob, 1_00)));
            work.add(() -> transferService.transfer(new TransferService.Command(
                    "pg-ba-" + n + "-" + System.nanoTime(), bob, alice, 1_00)));
        }

        List<TransferService.Result> results = invokeAll(work);

        assertThat(results).hasSize(20);
        assertThat(results).allMatch(r -> r.transfer().getStatus() == TransferStatus.COMPLETED);
        assertThat(walletService.get(alice).getBalanceMinor()).isEqualTo(50_00);
        assertThat(transferService.netAcrossAllWallets()).isZero();
    }

    @Test
    @DisplayName("concurrent retries of one key move money once on PostgreSQL")
    void idempotencyUnderRealUniqueConstraint() throws Exception {
        String key = "pg-race-" + System.nanoTime();

        List<TransferService.Result> results = runConcurrently(8, () ->
                transferService.transfer(new TransferService.Command(key, alice, bob, 10_00)));

        assertThat(results).extracting(r -> r.transfer().getId())
                .containsOnly(results.get(0).transfer().getId());
        assertThat(walletService.get(alice).getBalanceMinor()).isEqualTo(90_00);
    }

    @Test
    @DisplayName("the overdraft CHECK constraint exists on PostgreSQL")
    void overdraftConstraintEnforced() {
        var result = transferService.transfer(
                new TransferService.Command("pg-over-" + System.nanoTime(), alice, bob, 500_00));
        assertThat(result.transfer().getStatus()).isEqualTo(TransferStatus.REJECTED);
        assertThat(walletService.get(alice).getBalanceMinor()).isEqualTo(100_00);
    }

    private <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return task.call();
                }));
            }
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
        for (Future<T> f : futures) {
            try {
                out.add(f.get(60, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                throw new AssertionError("A concurrent transfer threw: " + e.getCause(), e.getCause());
            }
        }
        return out;
    }
}

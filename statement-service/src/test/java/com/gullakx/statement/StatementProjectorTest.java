package com.gullakx.statement;

import com.gullakx.common.events.TransferCompleted;
import com.gullakx.statement.domain.StatementEntry;
import com.gullakx.statement.repository.StatementEntryRepository;
import com.gullakx.statement.service.StatementProjector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The consumer side of the outbox contract.
 *
 * Delivery is at-least-once by design, so the only thing standing between a
 * customer's statement and a duplicated line is this consumer being idempotent.
 * These tests are that guarantee.
 */
@SpringBootTest
@ActiveProfiles("test")
class StatementProjectorTest {

    @Autowired
    private StatementProjector projector;
    @Autowired
    private StatementEntryRepository entries;

    private static long nextId() {
        return System.nanoTime() % 1_000_000_000L;
    }

    private static TransferCompleted event(long transferId, long source, long dest, long amount) {
        return new TransferCompleted(transferId, "key-" + transferId, source, dest, amount,
                "INR", Instant.parse("2026-03-14T12:00:00Z").toString());
    }

    @Test
    @DisplayName("one transfer becomes two lines, one on each side")
    void oneTransferTwoLines() {
        long transferId = nextId();
        long alice = nextId();
        long bob = alice + 1;

        assertThat(projector.project(event(transferId, alice, bob, 25_00))).isEqualTo(2);

        List<StatementEntry> aliceLines = projector.statementFor(alice);
        List<StatementEntry> bobLines = projector.statementFor(bob);

        assertThat(aliceLines).singleElement()
                .satisfies(e -> {
                    assertThat(e.getDirection()).isEqualTo("DEBIT");
                    assertThat(e.getCounterparty()).isEqualTo(bob);
                    assertThat(e.getAmountMinor()).isEqualTo(25_00);
                });
        assertThat(bobLines).singleElement()
                .satisfies(e -> {
                    assertThat(e.getDirection()).isEqualTo("CREDIT");
                    assertThat(e.getCounterparty()).isEqualTo(alice);
                });
    }

    @Test
    @DisplayName("a redelivered event adds nothing")
    void redeliveryIsIdempotent() {
        long transferId = nextId();
        long alice = nextId();
        long bob = alice + 1;
        TransferCompleted e = event(transferId, alice, bob, 10_00);

        assertThat(projector.project(e)).isEqualTo(2);
        // At-least-once means this happens routinely: the publisher crashed
        // before marking the outbox row, or Kafka rebalanced mid-batch.
        assertThat(projector.project(e)).isZero();
        assertThat(projector.project(e)).isZero();

        assertThat(entries.countByTransferId(transferId)).isEqualTo(2);
        assertThat(projector.balanceFor(alice)).isEqualTo(-10_00);
        assertThat(projector.balanceFor(bob)).isEqualTo(10_00);
    }

    @Test
    @DisplayName("concurrent redelivery still writes one line per side")
    void concurrentRedeliveryIsIdempotent() throws Exception {
        long transferId = nextId();
        long alice = nextId();
        long bob = alice + 1;
        TransferCompleted e = event(transferId, alice, bob, 5_00);

        int threads = 6;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return projector.project(e);
                }));
            }
            start.countDown();

            int total = 0;
            for (Future<Integer> f : futures) total += f.get(30, TimeUnit.SECONDS);

            // Two rebalancing consumers can both pass the existsBy check before
            // either commits. The UNIQUE constraint is what makes the outcome
            // the same as the single-threaded case.
            assertThat(total).isEqualTo(2);
            assertThat(entries.countByTransferId(transferId)).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("the statement balance matches the sum of its lines")
    void balanceDerivesFromLines() {
        long alice = nextId();
        long bob = alice + 1;

        projector.project(event(nextId(), alice, bob, 30_00));
        projector.project(event(nextId() + 1, bob, alice, 12_00));

        assertThat(projector.balanceFor(alice)).isEqualTo(-30_00 + 12_00);
        assertThat(projector.balanceFor(bob)).isEqualTo(30_00 - 12_00);
    }

    @Test
    @DisplayName("statements are newest first")
    void newestFirst() {
        long alice = nextId();
        long bob = alice + 1;

        projector.project(new TransferCompleted(nextId(), "k1", alice, bob, 1_00, "INR",
                "2026-03-10T09:00:00Z"));
        projector.project(new TransferCompleted(nextId() + 1, "k2", alice, bob, 2_00, "INR",
                "2026-03-14T09:00:00Z"));

        assertThat(projector.statementFor(alice))
                .extracting(StatementEntry::getAmountMinor)
                .containsExactly(2_00L, 1_00L);
    }

    @Test
    @DisplayName("an unparseable timestamp does not lose the money movement")
    void malformedTimestampStillRecorded() {
        long transferId = nextId();
        long alice = nextId();
        long bob = alice + 1;

        // Losing the exact time is recoverable; losing the line is not.
        var broken = new TransferCompleted(transferId, "k", alice, bob, 7_00, "INR", "not-a-date");
        assertThat(projector.project(broken)).isEqualTo(2);
        assertThat(projector.statementFor(alice)).hasSize(1);
    }

    @Test
    @DisplayName("a wallet with no activity has an empty statement, not an error")
    void emptyStatement() {
        assertThat(projector.statementFor(nextId())).isEmpty();
        assertThat(projector.balanceFor(nextId())).isZero();
    }
}

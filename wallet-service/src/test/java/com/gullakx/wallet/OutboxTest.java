package com.gullakx.wallet;

import com.gullakx.wallet.domain.OutboxEvent;
import com.gullakx.wallet.messaging.EventPublisher;
import com.gullakx.wallet.messaging.OutboxDispatcher;
import com.gullakx.wallet.messaging.TransferCompleted;
import com.gullakx.wallet.repository.OutboxRepository;
import com.gullakx.wallet.service.TransferService;
import com.gullakx.wallet.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The outbox exists to close the gap between "the money moved" and "somebody was
 * told". These tests pin down both halves of that: the event is written with the
 * transfer or not at all, and a broker that is down delays delivery rather than
 * losing it.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(OutboxTest.ControllablePublisherConfig.class)
@TestPropertySource(properties = "gullakx.events.dispatch-interval-ms=3600000")
class OutboxTest {

    /**
     * A hand-written publisher rather than a Mockito spy.
     *
     * Partly because it reads better - `publisher.failOn = e -> true` says what
     * it does more plainly than a stubbing chain - and partly because mocking
     * needs bytecode instrumentation, which breaks whenever the JDK moves ahead
     * of the mocking library. A twelve-line class has no such dependency and
     * runs on any JDK.
     */
    static class ControllablePublisher implements EventPublisher {
        final List<OutboxEvent> published = new ArrayList<>();
        volatile Predicate<OutboxEvent> failOn = event -> false;

        @Override
        public void publish(OutboxEvent event) {
            if (failOn.test(event)) {
                throw new IllegalStateException("broker unreachable");
            }
            published.add(event);
        }

        @Override
        public String describe() {
            return "controllable (test)";
        }

        void reset() {
            published.clear();
            failOn = event -> false;
        }
    }

    @TestConfiguration
    static class ControllablePublisherConfig {
        @Bean
        @Primary
        ControllablePublisher controllablePublisher() {
            return new ControllablePublisher();
        }
    }

    @Autowired
    private WalletService walletService;
    @Autowired
    private TransferService transferService;
    @Autowired
    private OutboxRepository outbox;
    @Autowired
    private OutboxDispatcher dispatcher;
    @Autowired
    private ObjectMapper json;

    @Autowired
    private ControllablePublisher publisher;

    private Long alice;
    private Long bob;

    @BeforeEach
    void openWallets() {
        long stamp = System.nanoTime();
        alice = walletService.openWallet("alice-" + stamp, "INR", 100_00).getId();
        bob = walletService.openWallet("bob-" + stamp, "INR", 0).getId();
        publisher.reset();
    }

    private List<OutboxEvent> unpublished() {
        return outbox.findByPublishedAtIsNullOrderByIdAsc(Limit.of(100));
    }

    @Test
    @DisplayName("a completed transfer writes an event describing it")
    void eventWrittenWithTransfer() throws Exception {
        long before = outbox.countByPublishedAtIsNull();

        var result = transferService.transfer(
                new TransferService.Command("evt-" + System.nanoTime(), alice, bob, 25_00));

        assertThat(outbox.countByPublishedAtIsNull()).isEqualTo(before + 1);

        OutboxEvent event = unpublished().stream()
                .filter(e -> e.getAggregateId().equals(String.valueOf(alice)))
                .reduce((a, b) -> b)
                .orElseThrow();

        assertThat(event.getEventType()).isEqualTo(TransferCompleted.EVENT_TYPE);
        assertThat(event.isPublished()).isFalse();

        TransferCompleted payload = json.readValue(event.getPayload(), TransferCompleted.class);
        assertThat(payload.transferId()).isEqualTo(result.transfer().getId());
        assertThat(payload.sourceWalletId()).isEqualTo(alice);
        assertThat(payload.destWalletId()).isEqualTo(bob);
        assertThat(payload.amountMinor()).isEqualTo(25_00);
        assertThat(payload.currency()).isEqualTo("INR");
    }

    @Test
    @DisplayName("the event carries the facts, so a consumer need not call back")
    void eventIsSelfContained() throws Exception {
        transferService.transfer(new TransferService.Command("sc-" + System.nanoTime(), alice, bob, 10_00));
        OutboxEvent event = unpublished().stream().reduce((a, b) -> b).orElseThrow();

        TransferCompleted payload = json.readValue(event.getPayload(), TransferCompleted.class);
        // Everything a statement or notification service needs, without a
        // synchronous round trip back to this service.
        assertThat(payload.idempotencyKey()).isNotBlank();
        assertThat(payload.occurredAt()).isNotBlank();
        assertThat(payload.currency()).isNotBlank();
    }

    @Test
    @DisplayName("a rejected transfer publishes nothing")
    void noEventForRejection() {
        long before = outbox.countByPublishedAtIsNull();

        transferService.transfer(new TransferService.Command("rej-" + System.nanoTime(), alice, bob, 500_00));

        // The counterpart to the atomicity guarantee: nobody is told about a
        // transfer that did not happen.
        assertThat(outbox.countByPublishedAtIsNull()).isEqualTo(before);
    }

    @Test
    @DisplayName("a failed command leaves no event behind")
    void noEventWhenTransferThrows() {
        long before = outbox.countByPublishedAtIsNull();

        assertThatThrownBy(() -> transferService.transfer(
                new TransferService.Command("bad-" + System.nanoTime(), alice, alice, 10_00)))
                .hasMessageContaining("SELF_TRANSFER_NOT_ALLOWED");

        assertThat(outbox.countByPublishedAtIsNull()).isEqualTo(before);
    }

    @Test
    @DisplayName("draining publishes pending events and marks them")
    void dispatcherDrains() {
        transferService.transfer(new TransferService.Command("d1-" + System.nanoTime(), alice, bob, 5_00));
        transferService.transfer(new TransferService.Command("d2-" + System.nanoTime(), alice, bob, 5_00));

        int published = dispatcher.drainOnce();

        assertThat(published).isGreaterThanOrEqualTo(2);
        assertThat(dispatcher.backlog()).isZero();
        assertThat(publisher.published).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("a broker that is down delays delivery, it does not lose it")
    void brokerFailureIsRecoverable() {
        transferService.transfer(new TransferService.Command("f1-" + System.nanoTime(), alice, bob, 7_00));

        publisher.failOn = event -> true;

        assertThat(dispatcher.drainOnce()).isZero();
        assertThat(dispatcher.backlog()).isPositive();

        OutboxEvent stuck = unpublished().stream().reduce((a, b) -> b).orElseThrow();
        assertThat(stuck.getAttempts()).isPositive();
        // Recorded on the row, not only in a log line that will have rotated
        // away by the time anyone investigates.
        assertThat(stuck.getLastError()).contains("broker unreachable");

        // Broker comes back; the backlog goes out. Nothing was lost.
        publisher.reset();
        assertThat(dispatcher.drainOnce()).isPositive();
        assertThat(dispatcher.backlog()).isZero();
    }

    @Test
    @DisplayName("one poisonous event does not block the ones behind it")
    void oneBadEventDoesNotStallTheQueue() {
        transferService.transfer(new TransferService.Command("p1-" + System.nanoTime(), alice, bob, 3_00));
        transferService.transfer(new TransferService.Command("p2-" + System.nanoTime(), alice, bob, 3_00));

        List<OutboxEvent> pending = unpublished();
        Long firstId = pending.get(0).getId();

        publisher.failOn = event -> event.getId().equals(firstId);

        dispatcher.drainOnce();

        // The healthy events went out; only the poison one remains.
        assertThat(unpublished()).extracting(OutboxEvent::getId).containsExactly(firstId);
    }
}

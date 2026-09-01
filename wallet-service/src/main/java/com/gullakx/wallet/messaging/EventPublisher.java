package com.gullakx.wallet.messaging;

import com.gullakx.wallet.domain.OutboxEvent;

/**
 * Where an outbox row goes once it is ready to leave.
 *
 * An interface so the dispatcher, the outbox semantics and every test can run
 * without a broker. Requiring Kafka to be up before `mvn verify` works would
 * make the tests that matter most - the ones asserting an event is written
 * atomically with the money moving - the hardest ones to run.
 */
public interface EventPublisher {

    /** @throws RuntimeException if the event could not be handed over. */
    void publish(OutboxEvent event);

    String describe();
}

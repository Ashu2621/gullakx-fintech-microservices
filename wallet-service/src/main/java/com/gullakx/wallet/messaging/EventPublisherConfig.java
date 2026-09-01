package com.gullakx.wallet.messaging;

import com.gullakx.wallet.domain.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Chooses where outbox rows go.
 *
 * Both publishers are declared as {@code @Bean} methods rather than annotated
 * {@code @Component}s on purpose: {@code @ConditionalOnMissingBean} is evaluated
 * against the beans registered so far, and during component scanning that order
 * is not defined. On a {@code @Bean} method inside a configuration class it is,
 * so "fall back to logging unless Kafka is configured" actually means that.
 */
@Configuration
public class EventPublisherConfig {

    private static final Logger log = LoggerFactory.getLogger(EventPublisherConfig.class);

    /**
     * Publishes to Kafka. Only created when a broker is configured, so the
     * service — and the whole test suite — runs without one.
     */
    @Bean
    @ConditionalOnProperty(name = "gullakx.events.kafka.enabled", havingValue = "true")
    public EventPublisher kafkaEventPublisher(
            KafkaTemplate<String, String> kafka,
            @org.springframework.beans.factory.annotation.Value("${gullakx.events.kafka.topic:wallet.events}") String topic,
            @org.springframework.beans.factory.annotation.Value("${gullakx.events.kafka.send-timeout-seconds:10}") long timeoutSeconds) {

        return new EventPublisher() {
            @Override
            public void publish(OutboxEvent event) {
                try {
                    // Blocking on the ack is the point: the row must not be
                    // marked published until the broker has accepted it.
                    // Fire-and-forget would turn the outbox back into a system
                    // that loses events, which is what it exists to prevent.
                    //
                    // The wallet id is the message key, which is what makes
                    // ordering useful: Kafka orders within a partition, and
                    // keying by wallet puts every event for one wallet on the
                    // same one. Keyed by transfer id, two transfers on a wallet
                    // could be consumed out of order and a statement built from
                    // them would show an impossible run of balances.
                    kafka.send(topic, event.getAggregateId(), event.getPayload())
                            .get(timeoutSeconds, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted publishing event " + event.getId(), interrupted);
                } catch (Exception failure) {
                    throw new IllegalStateException("Kafka rejected event " + event.getId(), failure);
                }
            }

            @Override
            public String describe() {
                return "kafka:" + topic;
            }
        };
    }

    /**
     * Used when no broker is configured — local runs and tests.
     *
     * It logs and succeeds, so outbox rows drain rather than piling up in a
     * developer's database forever. That is a deliberate lie about delivery,
     * confined to here, which is why {@code describe()} names itself in the
     * startup log: a service reporting {@code logging} is not delivering
     * anywhere, and that should be visible rather than inferred.
     */
    @Bean
    @ConditionalOnMissingBean(EventPublisher.class)
    public EventPublisher loggingEventPublisher() {
        return new EventPublisher() {
            @Override
            public void publish(OutboxEvent event) {
                log.info("event {} [{}] {}", event.getEventType(), event.getAggregateId(), event.getPayload());
            }

            @Override
            public String describe() {
                return "logging (no broker configured)";
            }
        };
    }
}

package com.gullakx.statement.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gullakx.common.events.TransferCompleted;
import com.gullakx.statement.service.StatementProjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads `wallet.events` and hands each one to the projector.
 *
 * Thin on purpose — it deserialises, dispatches, and does nothing else. The
 * interesting behaviour is in StatementProjector, where it can be tested
 * without a broker.
 *
 * Only active when Kafka is configured, so the service starts and its tests run
 * without one.
 */
@Component
@ConditionalOnProperty(name = "gullakx.events.kafka.enabled", havingValue = "true")
public class TransferEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransferEventListener.class);

    private final StatementProjector projector;
    private final ObjectMapper json;

    public TransferEventListener(StatementProjector projector, ObjectMapper json) {
        this.projector = projector;
        this.json = json;
    }

    @KafkaListener(topics = "${gullakx.events.kafka.topic:wallet.events}",
                   groupId = "${gullakx.events.kafka.group-id:statement-service}")
    public void onMessage(String payload) {
        try {
            TransferCompleted event = json.readValue(payload, TransferCompleted.class);
            int written = projector.project(event);
            log.debug("transfer {} produced {} statement line(s)", event.transferId(), written);
        } catch (Exception malformed) {
            // A message this consumer cannot read will never become readable by
            // being retried, so it is logged and acknowledged rather than
            // blocking the partition behind it forever. A dead-letter topic is
            // the next step; silently stalling every later event is not.
            log.error("skipping unreadable event: {}", malformed.getMessage());
        }
    }
}

package com.gullakx.wallet.messaging;

import com.gullakx.wallet.domain.OutboxEvent;
import com.gullakx.wallet.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Drains the outbox.
 *
 * Each event is published and then marked in its own short transaction, rather
 * than the batch being published inside one long one. That matters for a reason
 * that is easy to miss: holding a database transaction open across a network
 * call to a broker means a slow broker becomes a slow database, and the
 * connection pool drains before anyone notices the real cause.
 *
 * The cost is at-least-once delivery — publishing can succeed and the process
 * die before the row is marked, so the event goes out twice. That is the
 * correct trade here: a duplicate is recoverable by an idempotent consumer, a
 * lost payment notification is not.
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final int BATCH = 50;

    private final OutboxRepository outbox;
    private final EventPublisher publisher;

    public OutboxDispatcher(OutboxRepository outbox, EventPublisher publisher) {
        this.outbox = outbox;
        this.publisher = publisher;
        log.info("outbox dispatcher publishing via {}", publisher.describe());
    }

    @Scheduled(fixedDelayString = "${gullakx.events.dispatch-interval-ms:2000}")
    public void dispatch() {
        drainOnce();
    }

    /** @return how many events were published. Exposed so tests need no clock. */
    public int drainOnce() {
        List<OutboxEvent> pending = pending();
        int published = 0;

        for (OutboxEvent event : pending) {
            try {
                publisher.publish(event);
                markPublished(event.getId());
                published++;
            } catch (RuntimeException failure) {
                // One bad event must not stop the ones behind it, and the
                // failure is recorded on the row rather than only in a log line
                // that will have rotated away by the time anyone looks.
                markFailed(event.getId(), failure.getMessage());
                log.warn("outbox event {} failed to publish: {}", event.getId(), failure.getMessage());
            }
        }
        return published;
    }

    @Transactional(readOnly = true)
    protected List<OutboxEvent> pending() {
        return outbox.findByPublishedAtIsNullOrderByIdAsc(Limit.of(BATCH));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markPublished(Long id) {
        outbox.findById(id).ifPresent(e -> {
            e.markPublished();
            outbox.save(e);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markFailed(Long id, String error) {
        outbox.findById(id).ifPresent(e -> {
            e.markFailed(error);
            outbox.save(e);
        });
    }

    public long backlog() {
        return outbox.countByPublishedAtIsNull();
    }
}

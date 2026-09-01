package com.gullakx.statement.service;

import com.gullakx.common.events.TransferCompleted;
import com.gullakx.statement.domain.StatementEntry;
import com.gullakx.statement.repository.StatementEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Turns transfer events into statement lines.
 *
 * Deliberately separate from the Kafka listener. Projection is the logic worth
 * testing — that one transfer becomes two lines, that a redelivery adds none —
 * and keeping it out of the listener means those tests need a function call
 * rather than a broker.
 */
@Service
public class StatementProjector {

    private static final Logger log = LoggerFactory.getLogger(StatementProjector.class);

    private final StatementEntryRepository entries;

    /**
     * One transaction per line.
     *
     * A unique-constraint violation leaves the Hibernate session unusable, so a
     * duplicate on the debit side must not be able to poison the write of the
     * credit side. Giving each line its own transaction contains the failure to
     * the row that caused it.
     *
     * (Second time that rule has bitten in this codebase - the transfer service
     * learned it the same way.)
     */
    private final TransactionTemplate perLine;

    public StatementProjector(StatementEntryRepository entries, PlatformTransactionManager txManager) {
        this.entries = entries;
        this.perLine = new TransactionTemplate(txManager);
        this.perLine.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * @return how many lines were written: 2 for a new transfer, 0 for a
     *         redelivery. Returned rather than void so the caller — and the
     *         tests — can tell the difference.
     */
    public int project(TransferCompleted event) {
        Instant occurredAt = parseInstant(event.occurredAt());

        // One transfer, two statements: a debit on one side and a credit on the
        // other, and each wallet's owner sees only their own line.
        int written = record(event.sourceWalletId(), event, "DEBIT", event.destWalletId(), occurredAt);
        written += record(event.destWalletId(), event, "CREDIT", event.sourceWalletId(), occurredAt);
        return written;
    }

    private int record(long walletId, TransferCompleted event, String direction,
                       long counterparty, Instant occurredAt) {
        try {
            Integer wrote = perLine.execute(status -> {
                // The pre-check keeps the common redelivery cheap; the UNIQUE
                // constraint is what makes it correct, because two consumers on
                // the same partition after a rebalance can both pass this check
                // before either commits.
                if (entries.existsByWalletIdAndTransferId(walletId, event.transferId())) {
                    return 0;
                }
                entries.saveAndFlush(new StatementEntry(walletId, event.transferId(), direction,
                        counterparty, event.amountMinor(), event.currency(), occurredAt));
                return 1;
            });
            return wrote == null ? 0 : wrote;
        } catch (DataIntegrityViolationException duplicate) {
            // Lost the race, and the transaction above has already rolled back
            // cleanly. The line exists, which is the outcome we wanted.
            log.debug("statement line for wallet {} transfer {} already present",
                    walletId, event.transferId());
            return 0;
        }
    }

    @Transactional(readOnly = true)
    public List<StatementEntry> statementFor(long walletId) {
        return entries.findByWalletIdOrderByOccurredAtDescIdDesc(walletId);
    }

    @Transactional(readOnly = true)
    public long balanceFor(long walletId) {
        return statementFor(walletId).stream().mapToLong(StatementEntry::signedAmount).sum();
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException | NullPointerException malformed) {
            // A bad timestamp must not stop the line being recorded: losing the
            // money movement is worse than losing its exact time, and the
            // recorded_at column still bounds when it arrived.
            log.warn("event carried an unparseable occurredAt: {}", value);
            return Instant.now();
        }
    }
}

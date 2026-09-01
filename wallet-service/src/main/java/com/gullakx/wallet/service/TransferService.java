package com.gullakx.wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gullakx.wallet.domain.*;
import com.gullakx.common.events.TransferCompleted;
import com.gullakx.wallet.repository.LedgerEntryRepository;
import com.gullakx.wallet.repository.OutboxRepository;
import com.gullakx.wallet.repository.TransferRepository;
import com.gullakx.wallet.repository.WalletRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Moving money between two wallets.
 *
 * The whole service is one method, and nearly all of its complexity is about
 * two questions that only matter under concurrency and retries:
 * can this run twice, and can two of these run at once?
 */
@Service
public class TransferService {

    private final WalletRepository wallets;
    private final TransferRepository transfers;
    private final LedgerEntryRepository ledger;

    /** The unit of work. READ_COMMITTED plus explicit row locks - see transfer(). */
    private final TransactionTemplate unitOfWork;

    /**
     * A separate transaction, used only to read the winner after losing an
     * idempotency race. It has to be separate: a constraint violation leaves the
     * Hibernate session unusable, so the losing transaction cannot query its way
     * out of the problem it just caused. Recovery needs a clean session.
     */
    private final TransactionTemplate recoveryTx;

    private final OutboxRepository outbox;
    private final ObjectMapper json;

    public TransferService(WalletRepository wallets, TransferRepository transfers,
                           LedgerEntryRepository ledger, OutboxRepository outbox,
                           ObjectMapper json, PlatformTransactionManager txManager) {
        this.wallets = wallets;
        this.transfers = transfers;
        this.ledger = ledger;
        this.outbox = outbox;
        this.json = json;

        this.unitOfWork = new TransactionTemplate(txManager);
        this.unitOfWork.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        this.recoveryTx = new TransactionTemplate(txManager);
        this.recoveryTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record Command(String idempotencyKey, Long sourceWalletId, Long destWalletId, long amountMinor) {
    }

    public record Result(Transfer transfer, boolean replayed) {
    }

    /**
     * READ_COMMITTED plus explicit row locks, rather than SERIALIZABLE.
     *
     * SERIALIZABLE would also be correct, and on a busy wallet it would be
     * slower and noisier: conflicts surface as serialization failures the caller
     * has to retry, and the database picks the victim. Taking the locks
     * explicitly makes the contended resource obvious in the code, and turns a
     * conflict into a short wait instead of a rollback.
     */
    public Result transfer(Command cmd) {
        validateShape(cmd);
        try {
            return unitOfWork.execute(status -> apply(cmd));
        } catch (DataIntegrityViolationException collision) {
            // Lost the race for this idempotency key. The winner has committed,
            // so its answer is the answer.
            return recoveryTx.execute(status -> transfers.findByIdempotencyKey(cmd.idempotencyKey())
                    .map(t -> new Result(t, true))
                    .orElseThrow(() -> collision));
        }
    }

    private Result apply(Command cmd) {
        // 1. Idempotency — fast path. A retry that arrives after the original
        //    committed is answered from the record rather than re-executed.
        Optional<Transfer> existing = transfers.findByIdempotencyKey(cmd.idempotencyKey());
        if (existing.isPresent()) {
            return new Result(existing.get(), true);
        }

        // 2. Lock both wallets, always in ascending id order.
        //
        //    This ordering is the deadlock defence, and it is easy to get wrong.
        //    If A->B locks A then B, while B->A locks B then A, the two hold
        //    what the other needs and neither can proceed; the database breaks
        //    the tie by killing one, so a perfectly valid transfer fails under
        //    load. A global lock order means the pair can never form a cycle.
        List<Long> ids = List.of(cmd.sourceWalletId(), cmd.destWalletId())
                .stream()
                .sorted()
                .toList();

        Map<Long, Wallet> locked = wallets.lockAllById(ids).stream()
                .collect(Collectors.toMap(Wallet::getId, Function.identity()));

        Wallet source = require(locked, cmd.sourceWalletId(), "SOURCE_WALLET_NOT_FOUND");
        Wallet dest = require(locked, cmd.destWalletId(), "DEST_WALLET_NOT_FOUND");

        // 3. Business rules, evaluated only once the balances cannot move.
        if (!source.getCurrency().equals(dest.getCurrency())) {
            return reject(cmd, "CURRENCY_MISMATCH");
        }
        // canFund rather than a raw balance comparison: the funding account is
        // the source of money entering the system, so it is expected to run
        // negative and must not be blocked by an overdraft rule meant for
        // customer wallets.
        if (!source.canFund(cmd.amountMinor())) {
            return reject(cmd, "INSUFFICIENT_FUNDS");
        }

        // 4. Apply. Both sides, or neither — the transaction guarantees that.
        Transfer transfer = save(Transfer.completed(
                cmd.idempotencyKey(), source.getId(), dest.getId(), cmd.amountMinor()));

        source.debit(cmd.amountMinor());
        dest.credit(cmd.amountMinor());

        ledger.save(new LedgerEntry(transfer.getId(), source.getId(), Direction.DEBIT,
                cmd.amountMinor(), source.getBalanceMinor()));
        ledger.save(new LedgerEntry(transfer.getId(), dest.getId(), Direction.CREDIT,
                cmd.amountMinor(), dest.getBalanceMinor()));

        wallets.save(source);
        wallets.save(dest);

        // Same transaction as the money. The event commits with the transfer or
        // not at all - see V2__outbox.sql for why it is not published directly.
        outbox.save(new OutboxEvent(
                "transfer", String.valueOf(source.getId()), TransferCompleted.EVENT_TYPE,
                serialise(new TransferCompleted(
                        transfer.getId(), transfer.getIdempotencyKey(),
                        source.getId(), dest.getId(), cmd.amountMinor(),
                        source.getCurrency(), transfer.getCreatedAt().toString()))));

        return new Result(transfer, false);
    }

    /**
     * A rejection is recorded, not just returned.
     *
     * Otherwise a client retrying after "insufficient funds" gets a fresh
     * evaluation, and if a deposit landed in between, the same request produces
     * a different answer the second time. An idempotency key should mean "this
     * request has an answer", not "this request succeeded".
     */
    private Result reject(Command cmd, String reason) {
        Transfer rejected = save(Transfer.rejected(
                cmd.idempotencyKey(), cmd.sourceWalletId(), cmd.destWalletId(),
                cmd.amountMinor(), reason));
        return new Result(rejected, false);
    }

    /**
     * Persist the transfer record.
     *
     * Two retries can race past the step-1 lookup before either has committed,
     * so that application check is only an optimisation and the UNIQUE
     * constraint is the actual guarantee. The collision is deliberately not
     * handled here: it must escape so the transaction rolls back, and the
     * caller re-reads in a clean session.
     */
    private Transfer save(Transfer transfer) {
        // saveAndFlush, not save: the constraint has to be checked now rather
        // than at commit, so a collision surfaces here while the enclosing
        // transaction can still be rolled back cleanly.
        return transfers.saveAndFlush(transfer);
    }

    private String serialise(TransferCompleted event) {
        try {
            return json.writeValueAsString(event);
        } catch (JsonProcessingException impossible) {
            // A record of primitives cannot fail to serialise; if it somehow
            // does, failing the transfer is correct - a committed transfer with
            // no event is exactly what the outbox exists to prevent.
            throw new IllegalStateException("Could not serialise " + TransferCompleted.EVENT_TYPE, impossible);
        }
    }

    private static void validateShape(Command cmd) {
        if (cmd.idempotencyKey() == null || cmd.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("IDEMPOTENCY_KEY_REQUIRED");
        }
        if (cmd.amountMinor() <= 0) {
            throw new IllegalArgumentException("AMOUNT_MUST_BE_POSITIVE");
        }
        if (cmd.sourceWalletId() == null || cmd.destWalletId() == null) {
            throw new IllegalArgumentException("WALLET_REQUIRED");
        }
        if (cmd.sourceWalletId().equals(cmd.destWalletId())) {
            // Allowing this would let a wallet write two entries that cancel
            // out, which is noise in the ledger and never a real instruction.
            throw new IllegalArgumentException("SELF_TRANSFER_NOT_ALLOWED");
        }
    }

    private static Wallet require(Map<Long, Wallet> locked, Long id, String error) {
        Wallet wallet = locked.get(id);
        if (wallet == null) {
            throw new IllegalArgumentException(error);
        }
        return wallet;
    }

    /** Reconciliation: does the cached balance still match the ledger? */
    @Transactional(readOnly = true)
    public boolean reconciles(Long walletId) {
        Wallet wallet = wallets.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("WALLET_NOT_FOUND"));
        return wallet.getBalanceMinor() == ledger.derivedBalance(walletId);
    }

    /** System-wide invariant: the ledger nets to zero. */
    @Transactional(readOnly = true)
    public long netAcrossAllWallets() {
        return ledger.netAcrossAllWallets();
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> statement(Long walletId) {
        return ledger.findByWalletIdOrderByIdAsc(walletId).stream()
                .sorted(Comparator.comparing(LedgerEntry::getId))
                .toList();
    }
}

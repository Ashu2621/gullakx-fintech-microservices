-- A read model, not a second source of truth.
--
-- Every row here is derived from a transfer.completed event. If this table were
-- dropped it could be rebuilt by replaying the topic, which is the property
-- that makes it safe to change its shape later: the wallet ledger stays
-- authoritative and this is only a convenient projection of it.

CREATE TABLE statement_entries (
    id            BIGSERIAL PRIMARY KEY,
    wallet_id     BIGINT       NOT NULL,
    transfer_id   BIGINT       NOT NULL,
    direction     VARCHAR(6)   NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    counterparty  BIGINT       NOT NULL,
    amount_minor  BIGINT       NOT NULL CHECK (amount_minor > 0),
    currency      VARCHAR(3)   NOT NULL,
    occurred_at   TIMESTAMP    NOT NULL,
    recorded_at   TIMESTAMP    NOT NULL,

    -- The whole reason this consumer can be safe.
    --
    -- Delivery is at-least-once: the publisher marks an outbox row only after
    -- the broker accepts it, so a crash in that gap republishes. Kafka's own
    -- redelivery on a rebalance does the same. Without this constraint a
    -- statement would grow duplicate lines every time either happens, and the
    -- balance a customer reads would drift away from the ledger.
    CONSTRAINT uq_statement_entry UNIQUE (wallet_id, transfer_id)
);

CREATE INDEX idx_statement_wallet ON statement_entries (wallet_id, occurred_at DESC);

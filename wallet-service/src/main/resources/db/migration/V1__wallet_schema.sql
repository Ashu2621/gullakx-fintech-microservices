-- Wallet ledger.
--
-- DESIGN NOTE — money is stored as integer minor units, never a decimal type
-- and never a float. 1234 means Rs 12.34. Floating point cannot represent 0.1
-- exactly, so a system that adds balances in double slowly drifts away from the
-- truth, and the drift shows up as money that does not exist.
--
-- Every CHECK below is a real constraint rather than documentation. The
-- application validates the same rules and returns friendly errors, but the
-- database is the layer that cannot be bypassed by a bug in a new code path,
-- an admin script, or a service that gets written next year.

CREATE TABLE wallets (
    id             BIGSERIAL PRIMARY KEY,
    owner_id       VARCHAR(64)  NOT NULL,
    -- VARCHAR rather than CHAR: CHAR pads to width, so a comparison against
    -- an unpadded value silently fails on some drivers. ISO-4217 is always 3
    -- characters, and the length is asserted below rather than by the type.
    currency       VARCHAR(3)   NOT NULL CHECK (LENGTH(currency) = 3),
    balance_minor  BIGINT       NOT NULL DEFAULT 0,
    -- A funding account: the counterparty for money entering or leaving the
    -- system. Without one, an opening balance would have to be written straight
    -- onto the wallet row, which puts money in a balance that no ledger entry
    -- explains -- and once that is allowed, the ledger is no longer the source
    -- of truth and reconciliation cannot mean anything.
    --
    -- It is the only account permitted to go negative, and that negative number
    -- is meaningful: it is exactly how much customer money the system is
    -- holding.
    is_system      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL,
    -- The last line of defence against an overdraft. If application logic ever
    -- lets a debit through that it should not, this rejects the write.
    CONSTRAINT ck_wallet_no_overdraft CHECK (is_system OR balance_minor >= 0),
    CONSTRAINT uq_wallet_owner_currency UNIQUE (owner_id, currency)
);

CREATE TABLE transfers (
    id                BIGSERIAL PRIMARY KEY,
    -- UNIQUE is what makes a retried transfer idempotent: the second attempt
    -- collides here instead of moving money twice. Enforced by the database
    -- because two concurrent retries can both pass an application-level
    -- "have I seen this key?" check before either has written a row.
    idempotency_key   VARCHAR(128) NOT NULL,
    source_wallet_id  BIGINT       NOT NULL REFERENCES wallets(id),
    dest_wallet_id    BIGINT       NOT NULL REFERENCES wallets(id),
    amount_minor      BIGINT       NOT NULL CHECK (amount_minor > 0),
    status            VARCHAR(16)  NOT NULL CHECK (status IN ('COMPLETED', 'REJECTED')),
    failure_reason    VARCHAR(64),
    created_at        TIMESTAMP    NOT NULL,
    CONSTRAINT uq_transfer_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_transfer_distinct_wallets CHECK (source_wallet_id <> dest_wallet_id)
);

-- Double-entry: every completed transfer writes exactly two rows here, one
-- DEBIT and one CREDIT of the same amount. The balance on `wallets` is a cached
-- projection of these rows, not the source of truth — which is what makes
-- reconciliation possible: sum the ledger, compare to the cached balance, and
-- a mismatch is a bug rather than an unanswerable question.
CREATE TABLE ledger_entries (
    id             BIGSERIAL PRIMARY KEY,
    transfer_id    BIGINT       NOT NULL REFERENCES transfers(id),
    wallet_id      BIGINT       NOT NULL REFERENCES wallets(id),
    direction      VARCHAR(6)   NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount_minor   BIGINT       NOT NULL CHECK (amount_minor > 0),
    -- Balance immediately after this entry was applied. Redundant with the sum
    -- of prior entries, and deliberately so: it turns "what did this wallet
    -- look like at 14:02?" into a lookup instead of a replay.
    balance_after  BIGINT       NOT NULL,
    created_at     TIMESTAMP    NOT NULL
);

CREATE INDEX idx_ledger_wallet ON ledger_entries (wallet_id, id);
CREATE INDEX idx_ledger_transfer ON ledger_entries (transfer_id);
CREATE INDEX idx_transfers_created ON transfers (created_at);

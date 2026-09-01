-- Transactional outbox.
--
-- DESIGN NOTE — why an event is not published directly from the transfer.
--
-- Writing to the database and publishing to a broker are two systems, and there
-- is no transaction spanning both. Every direct approach loses:
--
--   publish inside the transaction  -> the transaction can still roll back, and
--                                      a consumer has already been told about a
--                                      transfer that never happened;
--   publish after the commit        -> the process can die in the gap, and a
--                                      transfer exists that nobody was told
--                                      about.
--
-- So the event is written HERE, in the same transaction as the transfer and the
-- ledger entries. It commits or it does not, atomically with the money moving.
-- A separate dispatcher publishes rows from this table afterwards.
--
-- The guarantee that buys is at-least-once, not exactly-once: a dispatcher that
-- publishes and then dies before marking the row will publish again. Consumers
-- must be idempotent -- which is the same conclusion the transfer API reached,
-- for the same reason.

CREATE TABLE outbox_events (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    payload         TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    -- NULL until a dispatcher has handed it to the broker.
    published_at    TIMESTAMP,
    attempts        INTEGER      NOT NULL DEFAULT 0,
    last_error      VARCHAR(500)
);

-- Composite rather than partial. A partial index (`... WHERE published_at IS
-- NULL`) fits the dispatcher's query exactly and would be the better index on
-- PostgreSQL alone -- but it is PostgreSQL-specific syntax, and the test suite
-- runs on H2 so that `mvn verify` needs no database service. An index the tests
-- cannot create is an index the migration cannot be trusted to apply.
--
-- (published_at, id) serves `WHERE published_at IS NULL ORDER BY id` on both
-- engines; it is merely larger, because it also indexes history the dispatcher
-- never reads.
CREATE INDEX idx_outbox_unpublished ON outbox_events (published_at, id);

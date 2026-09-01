# GullakX

A wallet backend built around a double-entry ledger. Two Spring Boot services
behind a gateway: `auth-service` issues tokens, `wallet-service` moves money
without losing any, and `statement-service` builds a read model from the events
it emits.

The interesting part is not that money moves. It is that it still adds up when
twenty requests hit one balance at the same moment, and when a phone on a bad
connection retries the same payment four times.

## Run it

Needs JDK 21+ and Maven. The tests need neither a database nor Docker.

```bash
mvn verify   # 53 tests
```

To run the services, bring up Postgres and start each one:

```bash
docker compose up -d postgres
mvn -pl auth-service spring-boot:run     # :8081
mvn -pl wallet-service spring-boot:run   # :8082
mvn -pl api-gateway spring-boot:run      # :8080
```

## The decisions worth reading

### 1. The ledger is the truth; balances are a cache

Every completed transfer writes exactly two rows — one `DEBIT`, one `CREDIT`, of
the same amount. The `balance_minor` column on `wallets` is a projection of
those rows kept for O(1) reads, not the source of truth.

That distinction is what makes **reconciliation** a real check: sum a wallet's
ledger entries, compare against its cached balance, and a mismatch becomes a bug
you can find rather than a question nobody can answer.

### 2. Opening balances go through a funding account

The obvious way to open a wallet with ₹100 is to write `10000` onto the new row.
That one line breaks everything above it: the balance now exists with no ledger
entry explaining it, so reconciliation fails and the ledger is decorative.

So there is one funding account per currency, and opening a funded wallet is a
real transfer from it. It is the only account allowed to go negative, and that
negative is meaningful — it is exactly how much customer money the system holds.

*This was a bug, not foresight. The first version wrote the opening balance
directly onto the row, and the reconciliation test caught it.*

### 3. Money is integer minor units

`1234` means ₹12.34. Never `double`, never `float`. Floating point cannot
represent `0.1` exactly, so a system that sums balances in `double` drifts — and
the drift is money that does not exist.

### 4. Rows are locked before rules are evaluated

Both wallets are fetched with `SELECT … FOR UPDATE` before any balance check
runs. Without that, two requests both read a balance of 100, both conclude a
debit of 100 is affordable, and both write.

**Locks are always taken in ascending wallet id**, whatever the transfer
direction. Without a global lock order, `A→B` and `B→A` running concurrently
each hold what the other needs, and the database resolves it by killing a
perfectly valid transfer. A test runs ten transfers in each direction at once;
it hangs without the ordering.

`READ_COMMITTED` with explicit locks rather than `SERIALIZABLE`: it puts the
contended resource in the code where it can be seen, and turns a conflict into a
short wait instead of a rollback the caller has to retry.

### 5. Idempotency is enforced by the database

`UNIQUE (idempotency_key)` is the guarantee. The application checks for an
existing transfer first, but that check is only an optimisation — concurrent
retries can *all* pass it before any of them commits. A test fires eight threads
at one key and asserts the money moved once.

Two details that each cost a debugging round:

- **The collision is not caught in place.** A constraint violation leaves the
  Hibernate session unusable, so the losing transaction cannot query its way out
  of the problem it just caused. The exception escapes, the transaction rolls
  back, and the winner is re-read in a separate transaction.
- **Rejections are recorded too.** Otherwise a retry after `INSUFFICIENT_FUNDS`
  gets a fresh evaluation, and if a deposit landed in between, the same request
  yields a different answer depending on when it was retried. An idempotency key
  should mean *this request has an answer*, not *this request succeeded*.

### 6. Authentication is not authorization

The wallet API used to accept an `ownerId` in the request body and check
nothing at all. Any caller could open a wallet in someone else's name, read
anyone's statement, and transfer money out of any wallet by id — the auth
service existed, but nothing downstream looked at its tokens.

Two separate questions, answered in two separate places:

- **Who is calling** — a filter verifies the token and puts the user id in the
  security context. That is all it does.
- **May they touch *this* wallet** — checked in the controller, because it
  depends on which wallet was named.

Conflating them is how authorization holes appear: a filter that verified a
signature *feels* like it checked something, and endpoints start treating a
valid token as an entitled caller. Ownership now comes from the token and is
not something a request body can assert.

Two details:

- Someone else's wallet returns the same error as a wallet that does not exist.
  Distinguishing them turns the endpoint into a way to probe which ids are real.
- Only the *source* of a transfer is ownership-checked. Receiving money needs no
  permission from the recipient, and requiring it would make paying a stranger
  impossible.

Spring's default response to an unauthenticated request is 403, which tells a
client it is forbidden when what it needs to do is present a token. That is now
a 401 — the two are different instructions.

### 7. Events go through a transactional outbox

Publishing `transfer.completed` looks like one line at the end of the transfer.
It is not, because the database and the broker are two systems with no
transaction spanning them, and both obvious placements lose:

| | |
|---|---|
| publish **inside** the transaction | the transaction can still roll back — a consumer has been told about a transfer that never happened |
| publish **after** the commit | the process can die in the gap — a transfer exists that nobody was told about |

So the event is written to an `outbox_events` row **in the same transaction as
the ledger entries**. It commits with the money or not at all. A separate
dispatcher publishes those rows afterwards and marks them.

What that buys is *at-least-once*, not exactly-once: a dispatcher that publishes
and dies before marking the row will publish again. Consumers must be
idempotent — the same conclusion the transfer API reached, for the same reason.
A duplicate notification is recoverable; a lost one is not.

Two details worth the words:

- **Each event is marked in its own short transaction**, not the batch inside
  one long one. Holding a database transaction open across a network call to a
  broker means a slow broker becomes a slow database, and the connection pool
  drains before anyone works out why.
- **Kafka is off by default** and the publisher falls back to logging, so the
  service and the whole test suite run without a broker. The outbox is written
  either way — turning Kafka on later publishes the backlog rather than losing
  it.

### 8. A consumer that survives at-least-once delivery

`statement-service` reads `transfer.completed` and maintains a read model: one
transfer becomes two statement lines, a debit on one side and a credit on the
other.

The outbox guarantees *at-least-once*, so the only thing standing between a
customer's statement and a duplicated line is this consumer being idempotent.
`UNIQUE (wallet_id, transfer_id)` is that guarantee — the `existsBy` check
before it is only an optimisation, because two consumers on the same partition
after a rebalance can both pass it before either commits. A test races six
threads at one event and asserts two lines, not twelve.

Each line is written in **its own transaction**, which is the same lesson the
transfer service learned: a constraint violation leaves the Hibernate session
unusable, so a duplicate on the debit side must not be able to poison the write
of the credit side.

The projection logic sits apart from the Kafka listener, so the behaviour worth
testing needs a function call rather than a broker. And the statement is a read
model, not a second source of truth — drop the table and it rebuilds by
replaying the topic; the wallet ledger stays authoritative.

### 9. Auth service: the failure modes matter more than the happy path

- Login failures are **indistinguishable**, and the password is verified against
  a dummy hash even when no user exists, so neither the message nor the response
  time reveals whether an address holds an account.
- Email is lower-cased before storage — a `UNIQUE` index on the raw value would
  accept `Ana@x.com` and `ana@x.com` as two accounts for one person.
- Duplicate registration is caught at the index, not by a prior `existsByEmail`.
  A test races six concurrent registrations of one address and asserts exactly
  one account survives.
- BCrypt at cost 12, and passwords over 72 bytes are **rejected** rather than
  silently truncated, because BCrypt ignores everything past that point.
- `JwtIssuer` refuses to start on a secret shorter than the hash output.

## Tests

53 tests (5 need Docker).

| Area | What it pins down |
|---|---|
| Ledger invariants | double entries cancel, ledger nets to zero, balances reconcile, overdraft refused |
| Concurrency | 20 threads against a balance covering 10 — exactly 10 succeed, balance lands on 0 |
| Deadlock | 10 transfers each way, concurrently |
| Idempotency | replay, concurrent replay, and rejections replaying too |
| Authorization | forged, expired and foreign-issuer tokens refused; another user's wallet unreadable and undrainable |
| Outbox | event written atomically with the transfer, none written for a rejection, a downed broker delays rather than loses, one poison event does not stall the rest |
| Consumer | one transfer to two lines, redelivery adds nothing, concurrent redelivery adds nothing, balance derives from lines |
| PostgreSQL | the same concurrency assertions on a real engine via Testcontainers |
| Auth | registration race, enumeration resistance, password policy, token forgery |

**Two databases, on purpose.** The fast suite runs on H2 in PostgreSQL
compatibility mode, so `mvn verify` works on a clean checkout with no Docker
daemon. But a ledger whose correctness rests on row locking should be proven on
the engine that actually ships, and H2's lock-timeout and deadlock-detection
behaviour is its own — so `PostgresConcurrencyTest` re-runs the overdraft,
deadlock and idempotency assertions against a real PostgreSQL 16 through
Testcontainers.

It is annotated `disabledWithoutDocker`, so a missing daemon is a **visible
skip** rather than a silent pass. A test that quietly does nothing when its
infrastructure is absent is worse than no test, because the green tick still
appears. CI has Docker and runs them.

That split was worth having: the first outbox migration used a PostgreSQL
partial index, which H2 rejected outright — and an index the test database
cannot create is one the migration cannot be trusted to apply.

## Layout

```
common/            response envelope, JWT verification, event contracts
auth-service/      registration, login, JWT issuance         :8081
wallet-service/    ledger, transfers, reconciliation, outbox :8082
statement-service/ consumes transfer.completed, read model   :8083
api-gateway/       Spring Cloud Gateway routing              :8080
monitoring/        Prometheus scrape and alert config
```

## Honest status

`auth-service` and `wallet-service` are implemented and tested. Wallet
endpoints verify tokens and enforce ownership. `api-gateway` routes to both but
has no filters of its own — validation happens in each service, which is the
safer default anyway: a gateway that is the only thing checking tokens means
anything reaching a service directly is trusted.

Everything in `docker-compose.yml` is used by code. Redis, Elasticsearch,
Kibana and Jaeger sat in that file for a long time with nothing importing them,
which reads as an integration and was not one — they are gone rather than
decorative. Anything that returns will return with a consumer.

Kafka is off by default (`KAFKA_ENABLED`). The outbox makes the broker optional
rather than required: events accumulate and go out when one appears.

`api-gateway` routes to all three services but has no filters of its own —
validation happens in each service, which is the safer default anyway. A gateway
that is the only thing checking tokens means anything reaching a service
directly is trusted.

Known next steps: a dead-letter topic for events the consumer cannot read (it
currently logs and acknowledges them, which is better than stalling the
partition but is not a resting place), and gateway-level rate limiting.

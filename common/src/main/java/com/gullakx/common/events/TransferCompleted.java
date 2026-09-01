package com.gullakx.common.events;

/**
 * What a consumer is told when money moves.
 *
 * Deliberately a flat record of facts, not a reference the consumer has to
 * resolve. An event carrying only a transfer id forces every consumer back to
 * this service to find out what happened, which turns an asynchronous message
 * into a synchronous dependency and makes the publisher a single point of
 * failure for everyone downstream.
 *
 * `occurredAt` is when the transfer committed, not when the message was
 * published. Those differ by however long the outbox backlog is, and a consumer
 * building a statement needs the first one.
 *
 * It lives in `common` so the publisher and the consumer compile against one
 * definition. In a polyrepo the consumer would own a copy and tolerate drift on
 * purpose; here a single artifact means a field rename cannot silently break a
 * consumer, which is the more useful property at this size.
 */
public record TransferCompleted(
        long transferId,
        String idempotencyKey,
        long sourceWalletId,
        long destWalletId,
        long amountMinor,
        String currency,
        String occurredAt) {

    public static final String EVENT_TYPE = "transfer.completed";
}

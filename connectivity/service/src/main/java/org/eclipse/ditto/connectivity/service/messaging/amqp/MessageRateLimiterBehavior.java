/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.connectivity.service.messaging.amqp;

import java.time.Duration;

import org.eclipse.ditto.connectivity.service.config.Amqp10Config;
import org.eclipse.ditto.connectivity.service.config.ConnectionThrottlingConfig;

import akka.actor.AbstractActor;
import akka.actor.Actor;
import akka.actor.Timers;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Mixin for limitation of unacknowledged messages or messages
 * Subclasses must extend {@code AbstractActorWithTimers} (self-type requirement).
 * Necessary for AMQP 1.0 due to JMS client's not exposing protocol-level flow control to the application level
 * (throttling via prefetch-policy is not possible).
 *
 * @param <S> type of message IDs.
 */
interface MessageRateLimiterBehavior<S> extends Actor, Timers {

    /**
     * Require a logger for this actor.
     *
     * @return this actor's logger.
     */
    LoggingAdapter log();

    /**
     * Start message consumer. NOT required to be thread-safe. May be asynchronous.
     */
    void startMessageConsumerDueToRateLimit();

    /**
     * Stop message consumer. NOT required to be thread-safe. May be asynchronous.
     *
     * @param reason why the message consumer should be stopped.
     */
    void stopMessageConsumerDueToRateLimit(final String reason);

    /**
     * MUST be thread-safe, even though the result is not thread-safe.
     *
     * @return the rate limiter state.
     */
    MessageRateLimiter<S> getMessageRateLimiter();

    /**
     * Create the rate-limiter state to be included in the actor state.
     *
     * @param config the configuration.
     * @return the rate limiter as a part of the actor state.
     */
    default MessageRateLimiter<S> initMessageRateLimiter(final Amqp10Config config) {
        final ConnectionThrottlingConfig throttlingConfig = config.getConsumerConfig().getThrottlingConfig();
        if (throttlingConfig.isEnabled()) {
            // schedule periodic throughput check
            timers().startTimerAtFixedRate(Control.CHECK_RATE_LIMIT, Control.CHECK_RATE_LIMIT,
                    throttlingConfig.getInterval());
        }
        return MessageRateLimiter.of(config);
    }

    /**
     * Create the rate-limiting behavior to be included in the actor behavior.
     *
     * @return the rate-limiting behavior.
     */
    default AbstractActor.Receive getRateLimiterBehavior() {
        if (getMessageRateLimiter().isEnabled()) {
            return ReceiveBuilder.create()
                    .match(AckStatus.class, this::checkAckStatus)
                    .matchEquals(Control.CHECK_RATE_LIMIT, this::checkRateLimitAsScheduled)
                    .match(Forget.class, this::forgetPendingRedelivery)
                    .build();
        } else {
            return ReceiveBuilder.create().build();
        }
    }

    /**
     * Record an incoming message for rate limiting.
     * NOT thread-safe! Only call in the actor's thread!
     *
     * @param messageId the ID of the incoming message.
     */
    default void recordIncomingForRateLimit(final S messageId) {
        final MessageRateLimiter<S> rateLimiter = getMessageRateLimiter();
        if (rateLimiter.isEnabled()) {
            // message counted as in-flight and for current period
            rateLimiter.incoming(messageId);
            // message no longer counted as pending redelivery (this is THE redelivery)
            rateLimiter.forgetPendingRedelivery(messageId);
            timers().cancel(messageId);

            // only check for total throughput here; check in-flight at regular intervals
            checkRateLimitForConsumedThisPeriod(rateLimiter);
        }
    }

    /**
     * Record the acknowledgement of an incoming message for rate limiting.
     *
     * @param messageId ID of the incoming message.
     * @param isSuccess whether success is being acknowledged.
     * @param redeliver whether redelivery is requested in case of failure.
     */
    default void recordAckForRateLimit(final S messageId, final boolean isSuccess, final boolean redeliver) {
        if (getMessageRateLimiter().isEnabled()) {
            self().tell(new AckStatus<>(messageId, isSuccess, redeliver), self());
        }
    }

    private void checkRateLimitForConsumedThisPeriod(final MessageRateLimiter<S> rateLimiter) {
        if (rateLimiter.isMaxPerPeriodExceeded() && rateLimiter.isConsumerOpen()) {
            stopConsumerDueToRateLimit(rateLimiter, true, false);
        }
    }

    private void checkRateLimitAsScheduled(final Control trigger) {
        final MessageRateLimiter<S> rateLimiter = getMessageRateLimiter();
        // allocate more credit for consumption
        rateLimiter.reduceConsumedInPeriod();

        final boolean maxPerPeriodExceeded = rateLimiter.isMaxPerPeriodExceeded();
        final boolean maxInFlightExceeded = rateLimiter.isMaxInFlightExceeded();
        final boolean isConsumerOpen = rateLimiter.isConsumerOpen();
        final boolean shouldConsumerBeOpen = !maxPerPeriodExceeded && !maxInFlightExceeded;

        if (isConsumerOpen != shouldConsumerBeOpen) {
            if (shouldConsumerBeOpen) {
                startConsumerDueToRateLimit(rateLimiter);
            } else {
                stopConsumerDueToRateLimit(rateLimiter, maxPerPeriodExceeded, maxInFlightExceeded);
            }
        }
    }

    private void startConsumerDueToRateLimit(final MessageRateLimiter<S> rateLimiter) {
        logRateLimiter(rateLimiter, "Starting");
        startMessageConsumerDueToRateLimit();
        rateLimiter.setIsConsumerOpen(true);
    }

    private void stopConsumerDueToRateLimit(final MessageRateLimiter<S> rateLimiter,
            final boolean maxPerPeriodExceeded,
            final boolean maxInFlightExceeded) {

        logRateLimiter(rateLimiter, "Stopping");
        final String reason = getMessageConsumerStoppedReason(maxPerPeriodExceeded, maxInFlightExceeded);
        stopMessageConsumerDueToRateLimit(reason);
        rateLimiter.setIsConsumerOpen(false);
    }

    private void logRateLimiter(final MessageRateLimiter<S> rateLimiter, final String action) {
        log().info("RATELIMITER {} in-flight={} redelivering={} max-in-flight={}",
                action + " message consumer. period=" +
                        rateLimiter.getConsumedInPeriod() + "/" + rateLimiter.getMaxPerPeriod(),
                rateLimiter.getInFlight(),
                rateLimiter.getToBeRedelivered(),
                rateLimiter.getMaxInFlight()
        );
    }

    private void checkAckStatus(final AckStatus<S> ack) {
        final MessageRateLimiter<S> rateLimiter = getMessageRateLimiter();
        final boolean isRedeliveryRequested = !ack.isSuccess && ack.shouldRedeliver;
        if (isRedeliveryRequested) {
            rateLimiter.redeliveryRequested(ack.messageId);
            scheduleForgettingPendingRedelivery(ack.messageId, rateLimiter.getRedeliveryExpectationTimeout());
        } else {
            rateLimiter.terminallyAcknowledged(ack.messageId);
            timers().cancel(ack.messageId);
        }
    }

    private void scheduleForgettingPendingRedelivery(final S messageId, final Duration redeliveryExpectationTimeout) {
        timers().startSingleTimer(messageId, new Forget<>(messageId), redeliveryExpectationTimeout);
    }

    private void forgetPendingRedelivery(final Forget<S> forget) {
        getMessageRateLimiter().forgetPendingRedelivery(forget.messageId);
    }

    private String getMessageConsumerStoppedReason(final boolean maxPerPeriodExceeded,
            final boolean maxInFlightExceeded) {

        if (maxPerPeriodExceeded) {
            if (maxInFlightExceeded) {
                return "excessive messaging and too many unacknowledged commands";
            } else {
                return "excessive messaging";
            }
        } else {
            return "too many unacknowledged commands";
        }
    }

    final class Forget<S> {

        private final S messageId;

        public Forget(final S messageId) {
            this.messageId = messageId;
        }
    }

    final class AckStatus<S> {

        private final S messageId;
        private final boolean isSuccess;
        private final boolean shouldRedeliver;

        private AckStatus(final S messageId, final boolean isSuccess, final boolean shouldRedeliver) {
            this.messageId = messageId;
            this.isSuccess = isSuccess;
            this.shouldRedeliver = shouldRedeliver;
        }
    }

    enum Control {
        CHECK_RATE_LIMIT
    }
}

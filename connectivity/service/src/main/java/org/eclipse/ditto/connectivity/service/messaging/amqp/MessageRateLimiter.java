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
import java.util.HashSet;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.connectivity.service.config.Amqp10Config;
import org.eclipse.ditto.connectivity.service.config.ConnectionThrottlingConfig;

/**
 * Rate limiter for unacknowledged messages and total messages.
 * NOT thread-safe! Only use in actor's own thread! Do NOT use in futures!
 *
 * @param <S> type of message IDs
 */
@NotThreadSafe
final class MessageRateLimiter<S> {

    private final boolean enabled;
    private final int maxPerPeriod;
    private final int maxInFlight;
    private final Duration redeliveryExpectationTimeout;

    /**
     * Whether consumers are open before the next throttling decision.
     */
    private boolean isConsumerOpen = true;

    /**
     * Set of message IDs for which we have requested redelivery which has not arrived.
     */
    private final Set<S> pendingRedeliveries = new HashSet<>();

    /**
     * Counter for messages we have received and have not acknowledged.
     */
    private int inFlight = 0;

    /**
     * Counter for messages we have received in a given period.
     */
    private int consumedInPeriod = 0;

    private MessageRateLimiter(final ConnectionThrottlingConfig connectionThrottlingConfig,
            final Duration redeliveryExpectationTimeout) {
        this.enabled = connectionThrottlingConfig.isEnabled();
        this.maxPerPeriod = connectionThrottlingConfig.getLimit();
        this.maxInFlight = connectionThrottlingConfig.getMaxInFlight();
        this.redeliveryExpectationTimeout = maxDuration(redeliveryExpectationTimeout, Duration.ofSeconds(1L));
    }

    private MessageRateLimiter(final ConnectionThrottlingConfig connectionThrottlingConfig,
            final Duration redeliveryExpectationTimeout, final MessageRateLimiter<S> existingLimiter) {
        this.enabled = connectionThrottlingConfig.isEnabled();
        this.maxPerPeriod = connectionThrottlingConfig.getLimit();
        this.maxInFlight = connectionThrottlingConfig.getMaxInFlight();
        this.redeliveryExpectationTimeout = maxDuration(redeliveryExpectationTimeout, Duration.ofSeconds(1L));
        this.isConsumerOpen = existingLimiter.isConsumerOpen;
        this.pendingRedeliveries.addAll(existingLimiter.pendingRedeliveries);
        this.inFlight = existingLimiter.inFlight;
        this.consumedInPeriod = existingLimiter.consumedInPeriod;
    }

    static <S> MessageRateLimiter<S> of(final Amqp10Config config) {
        return new MessageRateLimiter<>(config.getConsumerConfig().getThrottlingConfig(),
                config.getConsumerConfig().getRedeliveryExpectationTimeout());
    }

    static <S> MessageRateLimiter<S> of(final Amqp10Config config, final MessageRateLimiter<S> existingLimiter) {
        return new MessageRateLimiter<>(config.getConsumerConfig().getThrottlingConfig(),
                config.getConsumerConfig().getRedeliveryExpectationTimeout(),
                existingLimiter);
    }

    /**
     * @return whether rate limiter is enabled.
     */
    boolean isEnabled() {
        return enabled;
    }

    /**
     * @return how many messages were consumed within this period.
     */
    int getConsumedInPeriod() {
        return consumedInPeriod;
    }

    /**
     * @return maximum number of messages we are allowed to consume within this period.
     */
    int getMaxPerPeriod() {
        return maxPerPeriod;
    }

    /**
     * @return current number of unacknowledged messages and messages expected to be redelivered
     */
    int getInFlight() {
        return inFlight;
    }

    /**
     * @return the number of messages expected to be redelivered
     */
    int getToBeRedelivered() {
        return pendingRedeliveries.size();
    }

    /**
     * @return maximum number of unacknowledged messages and messages expected to be redelivered
     */
    int getMaxInFlight() {
        return maxInFlight;
    }

    /**
     * @return after how long to forget about a pending unacknowledged message (could be consumed by someone else)
     */
    Duration getRedeliveryExpectationTimeout() {
        return redeliveryExpectationTimeout;
    }

    /**
     * Record an incoming message.
     *
     * @param messageId the ID of the incoming message.
     */
    void incoming(final S messageId) {
        inFlight++;
        consumedInPeriod++;
        pendingRedeliveries.remove(messageId);
    }

    /**
     * Record acknowledgement of an incoming message with no redelivery expected.
     *
     * @param messageId the ID of the incoming message.
     */
    void terminallyAcknowledged(final S messageId) {
        inFlight--;
    }

    /**
     * Record acknowledgement of an incoming message with redelivery expected.
     *
     * @param messageId the ID of the incoming message.
     */
    void redeliveryRequested(final S messageId) {
        inFlight--;
        pendingRedeliveries.add(messageId);
    }

    /**
     * Forget a message whose redelivery is expected.
     *
     * @param messageId the ID of the message whose redelivery is expected.
     */
    void forgetPendingRedelivery(final S messageId) {
        pendingRedeliveries.remove(messageId);
    }

    /**
     * @return whether the consumer is known to be open.
     */
    boolean isConsumerOpen() {
        return isConsumerOpen;
    }

    /**
     * Set whether the consumer is open.
     *
     * @param isConsumerOpen whether the consumer is open.
     */
    void setIsConsumerOpen(final boolean isConsumerOpen) {
        this.isConsumerOpen = isConsumerOpen;
    }

    /**
     * @return whether the number of messages consumed exceeded the maximum.
     */
    boolean isMaxPerPeriodExceeded() {
        return consumedInPeriod > maxPerPeriod;
    }

    /**
     * @return whether the number of unacknowledged/to-be-redelivered messages exceeded the maximum (e. g.,
     * when a subscriber of events generated by incoming commands is offline).
     */
    boolean isMaxInFlightExceeded() {
        return maxInFlight - inFlight - pendingRedeliveries.size() < 0;
    }

    /**
     * Called periodically to allocate "credit" to this consumer.
     * Setting "prefetch" policy does not work for JMS client unfortunately (checked version: 0.45.0).
     */
    void reduceConsumedInPeriod() {
        consumedInPeriod = Math.max(consumedInPeriod - maxPerPeriod, 0);
    }

    private static Duration maxDuration(final Duration d1, final Duration d2) {
        return d2.minus(d1).isNegative() ? d1 : d2;
    }

}

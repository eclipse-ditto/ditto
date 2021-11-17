/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.service.messaging.backoff;

import java.time.Duration;

import org.eclipse.ditto.connectivity.service.config.BackOffConfig;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;

import akka.actor.AbstractActor;
import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor that can be used to provide back-off behaviour for a message. You can use it the following way:
 * <ol>
 *     <li>Create the actor</li>
 *     <li>Create a back-off-message containing the message you need to back-off.
 *     Use {@link BackOffActor#createBackOffWithAnswerMessage(Object)} for this.</li>
 *     <li>Send the back-off-message to the actor</li>
 *     <li><i>Optionally</i>: Send a message created via {@link BackOffActor#createIsInBackOffMessage()} to the actor,
 *     which would respond with an {@link BackOffActor.IsInBackOffResponse} telling you the actor is currently in
 *     back-off mode.</li>
 *     <li>Receive the initially backed off message after the backoff duration</li>
 * </ol>
 *
 * <b>Caution:</b> When sending another backoff-message to the actor while it is still backing off a message, it
 * will <b>discard the previous message</b> and add some time to the backoff duration for this new message.
 * <b>Hence, it is not possible to backoff two messages at the same time.</b>
 */
public final class BackOffActor extends AbstractActorWithTimers {

    /**
     * Message for resetting the back-off timers.
     */
    private static final Object RESET_BACK_OFF = new Object();

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final RetryTimeoutStrategy retryTimeoutStrategy;

    @SuppressWarnings("unused")
    private BackOffActor(final BackOffConfig config) {
        this.retryTimeoutStrategy = DuplicationRetryTimeoutStrategy.fromConfig(config.getTimeoutConfig());
    }

    /**
     * Create Props for a new {@code BackOffActor}.
     *
     * @param config the config the actor uses.
     * @return Props for creating new instances of the actor.
     */
    public static Props props(final BackOffConfig config) {
        return Props.create(BackOffActor.class, config);
    }

    /**
     * Create a back-off-message that you can send to an instance of the {@code BackOffActor}. It will result in
     * backing off the {@code messageToBackOff}.
     *
     * @param messageToBackOff The message that should be backed off.
     * @param <A> Type of the message that is backed off.
     * @return back-off-message that can be sent to the {@code BackOffActor} and will
     */
    public static <A> BackOffWithAnswer<A> createBackOffWithAnswerMessage(final A messageToBackOff) {
        return new BackOffWithAnswer<>(messageToBackOff);
    }

    /**
     * Create a message that you can send to an instance of {@code BackOffActor} which will check if there is currently
     * a message backed off and respond with an instance of {@link BackOffActor.IsInBackOffResponse}.
     *
     * @return a message to ask the {@code BackOffActor} if it is currently backing off.
     */
    public static IsInBackOff createIsInBackOffMessage() {
        return IsInBackOff.INSTANCE;
    }

    @Override
    public AbstractActor.Receive createReceive() {
        return ReceiveBuilder.create()
                .match(BackOffWithAnswer.class, this::backOff)
                .match(BackOffWithSender.class, this::afterBackOff)
                .matchEquals(IsInBackOff.INSTANCE, this::handleIsInBackOff)
                .matchEquals(RESET_BACK_OFF, this::resetBackOff)
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                })
                .build();
    }

    private void backOff(final BackOffWithAnswer<?> backOffWithAnswer) {
        final Duration backOffTimeout = this.retryTimeoutStrategy.getNextTimeout();
        final Duration resetBackOffTimeout = backOffTimeout.multipliedBy(2L);

        log.debug("Going to back off for <{}> until sending answer: <{}>", backOffTimeout,
                backOffWithAnswer.getAnswer());

        this.getTimers()
                .startSingleTimer(InternalTimers.BACK_OFF, new BackOffWithSender<>(getSender(), backOffWithAnswer),
                        backOffTimeout);
        this.getTimers().startSingleTimer(InternalTimers.RESET_BACK_OFF, RESET_BACK_OFF, resetBackOffTimeout);
    }

    private void afterBackOff(final BackOffWithSender<?> backOffWithSender) {
        log.debug("BackOff finished, sending answer: <{}>", backOffWithSender.getAnswer());
        backOffWithSender.getSender().tell(backOffWithSender.getAnswer(), getSelf());
    }

    private void handleIsInBackOff(final Object o) {
        final var isInBackOff = this.isInBackOff();
        log.debug("Received IsInBackOff request, responding with: <{}>", isInBackOff);
        getSender().tell(new IsInBackOffResponse(isInBackOff), getSelf());
    }

    private boolean isInBackOff() {
        return this.getTimers().isTimerActive(InternalTimers.BACK_OFF);
    }

    private void resetBackOff(final Object resetBackOff) {
        log.debug("Resetting the back-off timers because no backOff happened for a long time.");
        this.retryTimeoutStrategy.reset();
    }

    /**
     * Response from {@code BackOffActor} if it is currently backing off a message.
     */
    public static class IsInBackOffResponse {

        private final boolean isInBackOff;

        private IsInBackOffResponse(final boolean isInBackOff) {
            this.isInBackOff = isInBackOff;
        }

        /**
         * @return {@code true} if currently backing off a message, {@code false} otherwise.
         */
        public boolean isInBackOff() {
            return this.isInBackOff;
        }
    }

    /**
     * Message to ask the {@code BackOffActor} if it is currently backing off a message.
     */
    private static class IsInBackOff {

        private static final IsInBackOff INSTANCE = new IsInBackOff();

        private IsInBackOff() { }
    }

    private static class BackOffWithAnswer<T> {

        private final T answer;

        private BackOffWithAnswer(final T answer) {
            this.answer = answer;
        }

        T getAnswer() {
            return this.answer;
        }
    }

    // does not extend BackOffWithAnswer to not accidentally break the match cases in the receive builder
    private static class BackOffWithSender<T> {

        private final ActorRef sender;
        private final T answer;

        private BackOffWithSender(final ActorRef sender, final BackOffWithAnswer<T> backOffWithAnswer) {
            this.sender = sender;
            this.answer = backOffWithAnswer.getAnswer();
        }

        ActorRef getSender() {
            return this.sender;
        }

        T getAnswer() {
            return this.answer;
        }
    }

    private enum InternalTimers {
        BACK_OFF,
        RESET_BACK_OFF
    }

}

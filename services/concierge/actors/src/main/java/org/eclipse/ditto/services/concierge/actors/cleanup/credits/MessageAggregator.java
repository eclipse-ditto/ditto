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
package org.eclipse.ditto.services.concierge.actors.cleanup.credits;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;

/**
 * Actor that
 * <ol>
 * <li>forwards its first message to a given recipient,</li>
 * <li>waits for a fixed number of messages of a certain type for a duration,</li>
 * <li>sends the list of received messages to the initial sender, and then</li>
 * <li>stops itself.</li>
 * <p>
 * This actor will never survive beyond the given timeout duration.
 * </ol>
 * @param <T> the type of the messages this aggregator aggregates
 */
final class MessageAggregator<T> extends AbstractActorWithTimers {

    /**
     * Message to signal timeout.
     */
    static final Object TIMEOUT = new AskTimeoutException("MessageAggregator.TIMEOUT");

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef initialReceiver;
    private final Class<T> messageClass;
    private final int expectedMessages;

    @Nullable
    private ActorRef sender = null;
    private List<T> messages = new ArrayList<>();

    @SuppressWarnings("unused")
    private MessageAggregator(
            final ActorRef initialReceiver,
            final Class<T> messageClass,
            final int expectedMessages,
            final Duration timeout) {

        this.initialReceiver = initialReceiver;
        this.expectedMessages = expectedMessages;
        this.messageClass = messageClass;
        getTimers().startSingleTimer(TIMEOUT, TIMEOUT, timeout);
    }

    /**
     * Create Props of a message aggregator.
     *
     * @param initialReceiver destination of the first message to forward.
     * @param messageClass the type of the messages this aggregator shall aggregate.
     * @param expectedMessages the expected amount of messages to aggregate.
     * @param timeout the timeout after which aggregation failed if not all {@code expectedMessages} were received.
     * @return the Props for this actor.
     */
    public static <T> Props props(final ActorRef initialReceiver, final Class<T> messageClass,
            final int expectedMessages, final Duration timeout) {

        return Props.create(MessageAggregator.class, initialReceiver, messageClass, expectedMessages, timeout);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchEquals(TIMEOUT, this::handleTimeout)
                .matchAny(firstMessage -> {
                    sender = getSender();
                    log.debug("MessageAggregator: Forwarding <{}> to <{}> on behalf of <{}>",
                            firstMessage, initialReceiver, sender);
                    initialReceiver.tell(firstMessage, getSelf());
                    if (expectedMessages > 0) {
                        getContext().become(listeningBehavior());
                    } else {
                        reportAndStop();
                    }
                })
                .build();
    }

    private Receive listeningBehavior() {
        return ReceiveBuilder.create()
                .match(messageClass, message -> {
                    log.debug("MessageAggregator: Received {} <{}>", messageClass, message);
                    messages.add(message);
                    if (messages.size() >= expectedMessages) {
                        reportAndStop();
                    }
                })
                .matchEquals(TIMEOUT, this::handleTimeout)
                .matchAny(message -> log.warning("MessageAggregator: not handled: <{}>", message))
                .build();
    }

    private void handleTimeout(final Object timeout) {
        log.error("MessageAggregator: Timeout. Received {}/{} sender=<{}> messages=<{}>",
                messages.size(), expectedMessages, sender, messages);
        reportAndStop();
    }

    private void reportAndStop() {
        if (sender != null) {
            log.debug("MessageAggregator: reporting to <{}> the collected messages <{}>", sender, messages);
            sender.tell(messages, getSelf());
        } else {
            log.error("MessageAggregator: This should not happen: sender==null. messages=<{}>", messages);
        }
        getContext().stop(getSelf());
    }
}

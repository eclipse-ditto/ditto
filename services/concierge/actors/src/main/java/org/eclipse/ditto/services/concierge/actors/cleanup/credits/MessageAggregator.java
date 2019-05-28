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
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Aggregates a certain number of messages of a certain type for a duration before stopping itself.
 */
public final class MessageAggregator<T> extends AbstractActorWithTimers {

    private static final Object TIMEOUT = "timeout";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef initialReceiver;
    private final Class<T> messageClass;
    private final int expectedMessages;

    @Nullable
    private ActorRef sender = null;
    private List<T> messages = new ArrayList<>();

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

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchEquals(TIMEOUT, this::handleTimeout)
                .matchAny(firstMessage -> {
                    sender = getSender();
                    log.debug("MessageAggregator: Forwarding <{}> to <{}> on behalf of <{}>",
                            firstMessage, initialReceiver, sender);
                    initialReceiver.tell(firstMessage, getSelf());
                    getContext().become(listeningBehavior());
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

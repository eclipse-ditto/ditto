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
package org.eclipse.ditto.services.connectivity.messaging;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor that collect a fixed number of command responses, which may be acknowledgements.
 */
final class ResponseCollectorActor extends AbstractActor {

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    private final List<CommandResponse<?>> commandResponses = new ArrayList<>();
    private int expectedCount = -1;
    private ActorRef querySender;

    private ResponseCollectorActor(final Duration timeout) {
        getContext().setReceiveTimeout(timeout);
    }

    static Props props(final Duration timeout) {
        return Props.create(ResponseCollectorActor.class, timeout);
    }

    static Input query() {
        return Query.INSTANCE;
    }

    static Input setCount(final int expectedCount) {
        return new SetCount(expectedCount);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchEquals(ReceiveTimeout.getInstance(), this::onReceiveTimeout)
                .matchEquals(Query.INSTANCE, this::onQuery)
                .match(SetCount.class, this::onSetCount)
                .match(CommandResponse.class, this::onCommandResponse)
                .matchAny(msg -> log.warning("Unhandled <{}>", msg))
                .build();
    }

    private void onReceiveTimeout(final ReceiveTimeout receiveTimeout) {
        log.debug("ReceiveTimeout");
        reportAndStop();
    }

    private void onQuery(final Query query) {
        querySender = getSender();
        log.debug("Query from <{}>", querySender);
        reportIfAllCollected();
    }

    private void onSetCount(final SetCount setCount) {
        expectedCount = setCount.count;
        log.debug("SetCount <{}>", expectedCount);
        reportIfAllCollected();
    }

    private void onCommandResponse(final CommandResponse<?> commandResponse) {
        log.debug("CommandResponse <{}>", commandResponse);
        commandResponses.add(commandResponse);
        reportIfAllCollected();
    }

    private void reportIfAllCollected() {
        if (querySender != null && expectedCount >= 0 && commandResponses.size() >= expectedCount) {
            reportAndStop();
        }
    }

    private void reportAndStop() {
        if (querySender != null) {
            querySender.tell(new Output(expectedCount, commandResponses), ActorRef.noSender());
        } else {
            log.error("ReceiveTimeout without Query");
        }
        getContext().stop(getSelf());
    }

    static final class Output {

        private final int expectedCount;
        private final List<CommandResponse<?>> commandResponses;

        private Output(final int expectedCount, final List<CommandResponse<?>> commandResponses) {
            this.expectedCount = expectedCount;
            this.commandResponses = commandResponses;
        }

        List<CommandResponse<?>> getCommandResponses() {
            return commandResponses;
        }

        boolean allExpectedResponsesArrived() {
            // always false if expectedCount < 0 (i. e., not set by a SetCount input signal)
            return expectedCount == commandResponses.size();
        }
    }

    /**
     * Input for the collector actor.
     */
    interface Input {}

    private enum Query implements Input {
        INSTANCE;
    }

    private static final class SetCount implements Input {

        private final int count;

        private SetCount(final int count) {
            this.count = count;
        }
    }

}

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
package org.eclipse.ditto.connectivity.service.messaging;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor that collects a fixed number of command responses, which may be acknowledgements.
 *
 * @since 1.2.0
 */
public final class ResponseCollectorActor extends AbstractActor {

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    private final List<CommandResponse<?>> commandResponses = new ArrayList<>();
    private int expectedCount = -1;
    private ActorRef querySender;
    private DittoRuntimeException error;

    @SuppressWarnings("unused") // called by static props() via Reflection
    private ResponseCollectorActor(final Duration timeout) {
        getContext().setReceiveTimeout(timeout);
    }

    static Props props(final Duration timeout) {
        return Props.create(ResponseCollectorActor.class, timeout);
    }

    static Input query() {
        return Query.INSTANCE;
    }

    public static Input setCount(final int expectedCount) {
        return new SetCount(expectedCount);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchEquals(ReceiveTimeout.getInstance(), this::onReceiveTimeout)
                .matchEquals(Query.INSTANCE, this::onQuery)
                .match(SetCount.class, this::onSetCount)
                .match(CommandResponse.class, this::onCommandResponse)
                .match(DittoRuntimeException.class, this::onDittoRuntimeException)
                .matchAny(msg -> log.warning("Unhandled <{}>", msg))
                .build();
    }

    private void onReceiveTimeout(final ReceiveTimeout receiveTimeout) {
        log.debug("ReceiveTimeout");
        reportOutputAndStop();
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
        log.withCorrelationId(commandResponse).debug("CommandResponse <{}>", commandResponse);
        commandResponses.add(commandResponse);
        reportIfAllCollected();
    }

    private void onDittoRuntimeException(final DittoRuntimeException dittoRuntimeException) {
        log.withCorrelationId(dittoRuntimeException)
                .debug("DittoRuntimeException <{}>", dittoRuntimeException);
        error = dittoRuntimeException;
        reportIfAllCollected();
    }

    private void reportIfAllCollected() {
        if (querySender != null && error != null && expectedCount >= 0) {
            reportAndStop(error);
        } else if (querySender != null && expectedCount >= 0 && commandResponses.size() >= expectedCount) {
            reportOutputAndStop();
        }
    }

    private void reportOutputAndStop() {
        reportAndStop(new Output(expectedCount, commandResponses));
    }

    private void reportAndStop(final Object output) {
        if (querySender != null) {
            querySender.tell(output, ActorRef.noSender());
        } else {
            log.error("ReceiveTimeout without Query");
        }
        getContext().cancelReceiveTimeout();
        getContext().stop(getSelf());
    }

    /**
     * Query output after all responses are received or after timeout.
     */
    public static final class Output {

        private final int expectedCount;
        private final List<CommandResponse<?>> commandResponses;

        private Output(final int expectedCount, final List<CommandResponse<?>> commandResponses) {
            this.expectedCount = expectedCount;
            this.commandResponses = commandResponses;
        }

        /**
         * @return The list of responses collected.
         */
        public List<CommandResponse<?>> getCommandResponses() {
            return commandResponses;
        }

        /**
         * @return Whether the expected number of responses arrived.
         */
        public boolean allExpectedResponsesArrived() {
            // always false if expectedCount < 0 (i. e., not set by a SetCount input signal)
            return expectedCount == commandResponses.size();
        }

        /**
         * @return list of failed responses.
         */
        public List<CommandResponse<?>> getFailedResponses() {
            return commandResponses.stream()
                    .filter(Output::isFailedResponse)
                    .toList();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[" +
                    "expectedCount=" + expectedCount +
                    ", commandResponses=" + commandResponses +
                    "]";
        }

        private static boolean isFailedResponse(final CommandResponse<?> response) {
            final boolean result;
            final var responseHttpStatus = response.getHttpStatus();
            if (isLiveResponse(response)) {
                /*
                 * Consider live responses only as failed acknowledgement when the response timed out.
                 * Otherwise it would not be possible to respond with an error status code to live messages.
                 */
                result = HttpStatus.REQUEST_TIMEOUT.equals(responseHttpStatus);
            } else {
                result = responseHttpStatus.isClientError() || responseHttpStatus.isServerError();
            }
            return result;
        }

        private static boolean isLiveResponse(final CommandResponse<?> response) {
            return CommandResponse.isMessageCommandResponse(response) ||
                    CommandResponse.isThingCommandResponse(response) &&
                            Signal.isChannelLive(response);
        }

    }

    /**
     * Input for the collector actor.
     */
    interface Input {}

    private enum Query implements Input {
        INSTANCE
    }

    private static final class SetCount implements Input {

        private final int count;

        private SetCount(final int count) {
            this.count = count;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[" + count + "]";
        }

        @Override
        public int hashCode() {
            return count;
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof SetCount setCount && setCount.count == count;
        }
    }
}

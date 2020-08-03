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
package org.eclipse.ditto.services.models.acks;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.signals.acks.base.AcknowledgementRequestDuplicateCorrelationIdException;
import org.eclipse.ditto.signals.base.Signal;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.InvalidActorNameException;
import akka.actor.Props;

/**
 * Starting an acknowledgement aggregator actor is more complex than simply call {@code actorOf}.
 * Thus starting logic is worth to be handled within its own class.
 *
 * @since 1.1.0
 */
public final class AcknowledgementAggregatorActorStarter implements Supplier<Optional<ActorRef>> {

    protected final ActorContext actorContext;
    protected final Signal<?> signal;
    protected final DittoHeaders dittoHeaders;
    protected final AcknowledgementConfig acknowledgementConfig;
    protected final HeaderTranslator headerTranslator;
    protected final Consumer<Object> responseSignalConsumer;

    private AcknowledgementAggregatorActorStarter(final ActorContext context,
            final Signal<?> signal,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer) {

        actorContext = checkNotNull(context, "context");
        this.signal = checkNotNull(signal, "signal");
        this.dittoHeaders = this.signal.getDittoHeaders();
        this.acknowledgementConfig = checkNotNull(acknowledgementConfig, "acknowledgementConfig");
        this.headerTranslator = checkNotNull(headerTranslator, "headerTranslator");
        this.responseSignalConsumer = checkNotNull(responseSignalConsumer, "responseSignalConsumer");
    }

    /**
     * Returns an instance of {@code AcknowledgementAggregatorActorStarter}.
     *
     * @param context the context to start the aggregator actor in.
     * @param signal the signal which potentially includes {@code AcknowledgementRequests}
     * based on which the AggregatorActor is started.
     * @param acknowledgementConfig provides configuration setting regarding acknowledgement handling.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param responseSignalConsumer a consumer which is invoked with the response signal, e.g. in order to send the
     * response over a channel to the user.
     * @return a means to start an acknowledgement forwarder actor.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static AcknowledgementAggregatorActorStarter getInstance(final ActorContext context,
            final Signal<?> signal,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer) {

        return new AcknowledgementAggregatorActorStarter(context, signal, acknowledgementConfig,
                headerTranslator, responseSignalConsumer);
    }

    @Override
    public Optional<ActorRef> get() {
        return tryToStartAckAggregatorActor();
    }

    private Optional<ActorRef> tryToStartAckAggregatorActor() {
        try {
            return startAckAggregatorActor();
        } catch (final InvalidActorNameException e) {
            // In case that the actor with that name already existed, the correlation-id was already used recently:
            throw getDuplicateCorrelationIdException(e);
        }
    }

    private Optional<ActorRef> startAckAggregatorActor() {
        final Props props = AcknowledgementAggregatorActor.props(signal, acknowledgementConfig, headerTranslator,
                responseSignalConsumer);
        final ActorRef actorRef =
                actorContext.actorOf(props, AcknowledgementAggregatorActor.determineActorName(dittoHeaders));
        return Optional.ofNullable(actorRef);
    }

    private DittoRuntimeException getDuplicateCorrelationIdException(final Throwable cause) {
        return AcknowledgementRequestDuplicateCorrelationIdException
                .newBuilder(dittoHeaders.getCorrelationId().orElse("?"))
                .dittoHeaders(dittoHeaders)
                .cause(cause)
                .build();
    }

}

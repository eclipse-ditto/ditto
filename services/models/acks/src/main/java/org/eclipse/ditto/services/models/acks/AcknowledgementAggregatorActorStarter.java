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

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.signals.acks.base.AcknowledgementRequestDuplicateCorrelationIdException;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;

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
final class AcknowledgementAggregatorActorStarter implements Supplier<Optional<ActorRef>> {

    private final ActorContext actorContext;
    private final ThingModifyCommand<?> thingModifyCommand;
    private final AcknowledgementConfig acknowledgementConfig;
    private final HeaderTranslator headerTranslator;
    private final Consumer<Object> responseSignalConsumer;

    private AcknowledgementAggregatorActorStarter(final ActorContext context,
            final ThingModifyCommand<?> thingModifyCommand,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer) {

        actorContext = checkNotNull(context, "context");
        this.thingModifyCommand = checkNotNull(thingModifyCommand, "thingModifyCommand");
        this.acknowledgementConfig = checkNotNull(acknowledgementConfig, "acknowledgementConfig");
        this.headerTranslator = checkNotNull(headerTranslator, "headerTranslator");
        this.responseSignalConsumer = checkNotNull(responseSignalConsumer, "responseSignalConsumer");
    }

    /**
     * Returns an instance of {@code AcknowledgementAggregatorActorStarter}.
     *
     * @param context the context to start the aggregator actor in.
     * @param thingModifyCommand the thing modify command which potentially includes {@code AcknowledgementRequests}
     * based on which the AggregatorActor is started.
     * @param acknowledgementConfig provides configuration setting regarding acknowledgement handling.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param responseSignalConsumer a consumer which is invoked with the response signal, e.g. in order to send the
     * response over a channel to the user.
     * @return a means to start an acknowledgement forwarder actor.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static AcknowledgementAggregatorActorStarter getInstance(final ActorContext context,
            final ThingModifyCommand<?> thingModifyCommand,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer) {

        return new AcknowledgementAggregatorActorStarter(context, thingModifyCommand, acknowledgementConfig,
                headerTranslator, responseSignalConsumer);
    }

    @Override
    public Optional<ActorRef> get() {
        return Optional.ofNullable(tryToStartAckAggregatorActor());
    }

    @Nullable
    private ActorRef tryToStartAckAggregatorActor() {
        try {
            return startAckAggregatorActor();
        } catch (final InvalidActorNameException e) {

            // In case that the actor with that name already existed, the correlation-id was already used recently:
            throw getDuplicateCorrelationIdException(e);
        }
    }

    @Nullable
    private ActorRef startAckAggregatorActor() {

        final DittoHeaders dittoHeaders = thingModifyCommand.getDittoHeaders();

        if (dittoHeaders.isResponseRequired()) {
            final Props props = AcknowledgementAggregatorActor.props(thingModifyCommand, acknowledgementConfig,
                    headerTranslator, responseSignalConsumer);
            return actorContext.actorOf(props, AcknowledgementAggregatorActor.determineActorName(dittoHeaders));
        } else {
            return null;
        }
    }

    private DittoRuntimeException getDuplicateCorrelationIdException(final Throwable cause) {
        return AcknowledgementRequestDuplicateCorrelationIdException
                .newBuilder(thingModifyCommand.getDittoHeaders().getCorrelationId().orElse("?"))
                .dittoHeaders(thingModifyCommand.getDittoHeaders())
                .cause(cause)
                .build();
    }

}

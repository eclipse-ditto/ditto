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

import java.util.Optional;
import java.util.function.Consumer;

import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.Props;

public final class MessageCommandAcknowledgementAggregatorActorStarter
        extends AbstractAcknowledgementAggregatorActorStarter<MessageCommand<?, ?>> {

    private MessageCommandAcknowledgementAggregatorActorStarter(final ActorContext context,
            final MessageCommand<?,?> signal,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer) {

        super(context, signal, acknowledgementConfig, headerTranslator, responseSignalConsumer);
    }


    /**
     * Returns an instance of {@code AcknowledgementAggregatorActorStarter}.
     *
     * @param context the context to start the aggregator actor in.
     * @param messageCommand the message command which potentially includes {@code AcknowledgementRequests}
     * based on which the AggregatorActor is started.
     * @param acknowledgementConfig provides configuration setting regarding acknowledgement handling.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param responseSignalConsumer a consumer which is invoked with the response signal, e.g. in order to send the
     * response over a channel to the user.
     * @return a means to start an acknowledgement forwarder actor.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static MessageCommandAcknowledgementAggregatorActorStarter getInstance(final ActorContext context,
            final MessageCommand<?,?> messageCommand,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer) {

        return new MessageCommandAcknowledgementAggregatorActorStarter(context, messageCommand,
                acknowledgementConfig, headerTranslator, responseSignalConsumer);
    }

    @Override
    protected Optional<ActorRef> startAckAggregatorActor() {
        final Props props = AcknowledgementAggregatorActor.props(signal, acknowledgementConfig, headerTranslator,
                responseSignalConsumer);
        final ActorRef actorRef =
                actorContext.actorOf(props, AcknowledgementAggregatorActor.determineActorName(dittoHeaders));
        return Optional.ofNullable(actorRef);
    }
}

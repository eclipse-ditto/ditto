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

/**
 * Starting an acknowledgement aggregator actor is more complex than simply call {@code actorOf}.
 * Thus starting logic is worth to be handled within its own class.
 *
 * @since 1.1.0
 */
abstract class AbstractAcknowledgementAggregatorActorStarter<T extends Signal<?>>
        implements Supplier<Optional<ActorRef>> {

    protected final ActorContext actorContext;
    protected final T signal;
    protected final DittoHeaders dittoHeaders;
    protected final AcknowledgementConfig acknowledgementConfig;
    protected final HeaderTranslator headerTranslator;
    protected final Consumer<Object> responseSignalConsumer;

    protected AbstractAcknowledgementAggregatorActorStarter(final ActorContext context,
            final T signal,
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

    protected abstract Optional<ActorRef> startAckAggregatorActor();

    private DittoRuntimeException getDuplicateCorrelationIdException(final Throwable cause) {
        return AcknowledgementRequestDuplicateCorrelationIdException
                .newBuilder(dittoHeaders.getCorrelationId().orElse("?"))
                .dittoHeaders(dittoHeaders)
                .cause(cause)
                .build();
    }

}

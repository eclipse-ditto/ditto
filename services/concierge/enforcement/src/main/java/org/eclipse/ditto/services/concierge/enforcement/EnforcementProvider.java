/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.concierge.enforcement;

import java.util.Optional;

import org.eclipse.ditto.services.utils.akka.controlflow.Filter;
import org.eclipse.ditto.services.utils.akka.controlflow.Pipe;
import org.eclipse.ditto.services.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.signals.base.Signal;

import akka.NotUsed;
import akka.stream.FanOutShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.SinkShape;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Sink;

/**
 * Provider interface for {@link AbstractEnforcement}.
 *
 * @param <T> the type of commands which are enforced.
 */
public interface EnforcementProvider<T extends Signal> {

    /**
     * The base class of the commands to which this enforcement applies.
     *
     * @return the command class.
     */
    Class<T> getCommandClass();

    /**
     * Test whether this enforcement provider is applicable for the given command.
     *
     * @param command the command.
     * @return whether this enforcement provider is applicable.
     */
    default boolean isApplicable(final T command) {
        return true;
    }

    /**
     * Creates an {@link AbstractEnforcement} for the given {@code context}.
     *
     * @param context the context.
     * @return the {@link AbstractEnforcement}.
     */
    AbstractEnforcement<T> createEnforcement(final AbstractEnforcement.Context context);

    /**
     * Create a processing unit of Akka stream graph. Unhandled messages are passed downstream.
     *
     * @param context the enforcement context.
     * @return a processing unit.
     */
    default Graph<FlowShape<WithSender, WithSender>, NotUsed> toGraph(
            final AbstractEnforcement.Context context) {

        return Pipe.joinFilteredSink(Filter.of(getCommandClass(), this::isApplicable),
                createEnforcement(context).toGraph());
    }

    /**
     * Convert this enforcement provider into a stream of contextual messages.
     *
     * @return the stream.
     */
    default Graph<FlowShape<Contextual<Object>, Contextual<Object>>, NotUsed> toContextualFlow() {

        final Sink<Contextual<T>, ?> sink = Sink.foreach(contextual ->
                createEnforcement(AbstractEnforcement.Context.of(contextual))
                        .enforceSafely(contextual.getMessage(), contextual.getSender(), contextual.getLog()));

        final Graph<FanOutShape2<Contextual<Object>, Contextual<T>, Contextual<Object>>, NotUsed> multiplexer =
                Pipe.multiplexBy(contextual ->
                        contextual.tryToMapMessage(message -> getCommandClass().isInstance(message)
                                ? Optional.of(getCommandClass().cast(message)).filter(this::isApplicable)
                                : Optional.empty()));

        return GraphDSL.create(builder -> {
            final FanOutShape2<Contextual<Object>, Contextual<T>, Contextual<Object>> fanout = builder.add(multiplexer);
            final SinkShape<Contextual<T>> handler = builder.add(sink);

            builder.from(fanout.out0()).to(handler);

            return FlowShape.of(fanout.in(), fanout.out1());
        });
    }
}

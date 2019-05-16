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
package org.eclipse.ditto.services.concierge.enforcement;

import java.util.Optional;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.controlflow.Filter;
import org.eclipse.ditto.signals.base.Signal;

import akka.NotUsed;
import akka.stream.FanOutShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.SinkShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

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
    AbstractEnforcement<T> createEnforcement(Contextual<T> context);

    /**
     * Convert this enforcement provider into a stream of contextual messages.
     *
     * @return the stream.
     */
    default Graph<FlowShape<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>>, NotUsed> toContextualFlow() {

        final Graph<FanOutShape2<Contextual<WithDittoHeaders>, Contextual<T>, Contextual<WithDittoHeaders>>, NotUsed>
                multiplexer =
                Filter.multiplexBy(contextual ->
                        contextual.tryToMapMessage(message -> getCommandClass().isInstance(message)
                                ? Optional.of(getCommandClass().cast(message)).filter(this::isApplicable)
                                : Optional.empty()));

        return GraphDSL.create(builder -> {
            final FanOutShape2<Contextual<WithDittoHeaders>, Contextual<T>, Contextual<WithDittoHeaders>> fanout =
                    builder.add(multiplexer);

            final Flow<Contextual<T>, Contextual<WithDittoHeaders>, NotUsed> enforcementFlow =
                    Flow.<Contextual<T>>create()
                            .flatMapConcat(contextual ->
                                    Source.fromCompletionStage(createEnforcement(contextual).enforceSafely())
                            );

            // by default, ignore unhandled messages:
            final SinkShape<Contextual<WithDittoHeaders>> logUnhandled = builder.add(Sink.ignore());

            final FlowShape<Contextual<T>, Contextual<WithDittoHeaders>> enforcementShape =
                    builder.add(enforcementFlow);

            builder.from(fanout.out0()).toInlet(enforcementShape.in());
            builder.from(fanout.out1()).to(logUnhandled);

            return FlowShape.of(fanout.in(), enforcementShape.out());
        });
    }

}

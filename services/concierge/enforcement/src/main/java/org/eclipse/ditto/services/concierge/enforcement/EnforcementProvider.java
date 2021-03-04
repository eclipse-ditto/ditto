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

import org.eclipse.ditto.model.base.entity.id.EntityId;
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

/**
 * Provider interface for {@link AbstractEnforcement}.
 *
 * @param <T> the type of commands which are enforced.
 */
public interface EnforcementProvider<T extends Signal<?>> {

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
     * Check whether a signal will change authorization of subsequent signals when dispatched.
     *
     * @param signal the signal.
     * @return whether the signal will change authorization.
     */
    default boolean changesAuthorization(final T signal) {
        return false;
    }

    /**
     * Convert this enforcement provider into a stream of enforcement tasks.
     *
     * @param preEnforcer failable future to execute before the actual enforcement.
     * @return the stream.
     */
    @SuppressWarnings("unchecked") // due to GraphDSL usage
    default Graph<FlowShape<Contextual<WithDittoHeaders<?>>, EnforcementTask>, NotUsed> createEnforcementTask(
            final PreEnforcer preEnforcer
    ) {

        final Graph<FanOutShape2<Contextual<WithDittoHeaders<?>>, Contextual<T>,
                Contextual<WithDittoHeaders<?>>>, NotUsed>
                multiplexer = Filter.multiplexBy(contextual -> contextual.tryToMapMessage(this::mapToHandledClass));

        return GraphDSL.create(builder -> {
            final FanOutShape2<Contextual<WithDittoHeaders<?>>, Contextual<T>, Contextual<WithDittoHeaders<?>>> fanout =
                    builder.add(multiplexer);

            final Flow<Contextual<T>, EnforcementTask, NotUsed> enforcementFlow =
                    Flow.fromFunction(contextual -> buildEnforcementTask(contextual, preEnforcer));

            // by default, ignore unhandled messages:
            final SinkShape<Contextual<WithDittoHeaders<?>>> unhandledSink = builder.add(Sink.ignore());

            final FlowShape<Contextual<T>, EnforcementTask> enforcementShape = builder.add(enforcementFlow);

            builder.from(fanout.out0()).toInlet(enforcementShape.in());
            builder.from(fanout.out1()).to(unhandledSink);

            return FlowShape.of(fanout.in(), enforcementShape.out());
        });
    }

    private EnforcementTask buildEnforcementTask(final Contextual<T> contextual, final PreEnforcer preEnforcer) {
        final T message = contextual.getMessage();
        final boolean changesAuthorization = changesAuthorization(message);
        final EntityId entityId = message.getEntityId();

        return EnforcementTask.of(entityId, changesAuthorization, () ->
                preEnforcer.withErrorHandlingAsync(contextual,
                        contextual.<WithDittoHeaders<?>>withMessage(null).withReceiver(null),
                        converted -> createEnforcement(converted).enforceSafely()
                )
        );
    }

    private Optional<T> mapToHandledClass(final Object message) {
        return getCommandClass().isInstance(message)
                ? Optional.of(getCommandClass().cast(message)).filter(this::isApplicable)
                : Optional.empty();
    }

}

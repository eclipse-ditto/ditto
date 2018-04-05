/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.authorization.util.enforcement;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.controlflow.Filter;
import org.eclipse.ditto.services.utils.akka.controlflow.WithSender;

import akka.NotUsed;
import akka.stream.FanOutShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.SinkShape;
import akka.stream.javadsl.GraphDSL;

/**
 * Provider interface for {@link Enforcement}.
 *
 * @param <T> the type of commands which are enforced.
 */
public interface EnforcementProvider<T extends WithDittoHeaders> {

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
     * Creates an {@link Enforcement} for the given {@code context}.
     *
     * @param context the context.
     * @return the {@link Enforcement}.
     */
    Enforcement<T> createEnforcement(final Enforcement.Context context);

    default Graph<FlowShape<WithSender, WithSender>, NotUsed> toGraph(
            final Enforcement.Context context) {

        return GraphDSL.create(builder -> {
            final FanOutShape2<WithSender, WithSender<T>, WithSender> filter =
                    builder.add(Filter.of(getCommandClass(), this::isApplicable));

            final SinkShape<WithSender<T>> enforcement =
                    builder.add(createEnforcement(context).toGraph());

            builder.from(filter.out0()).toInlet(enforcement.in());

            return FlowShape.of(filter.in(), filter.out1());
        });
    }
}

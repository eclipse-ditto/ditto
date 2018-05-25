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
package org.eclipse.ditto.services.concierge.enforcement;

import org.eclipse.ditto.services.utils.akka.controlflow.Filter;
import org.eclipse.ditto.services.utils.akka.controlflow.Pipe;
import org.eclipse.ditto.services.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.signals.base.Signal;

import akka.NotUsed;
import akka.stream.FlowShape;
import akka.stream.Graph;

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
}

/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

import akka.event.DiagnosticLoggingAdapter;

/**
 * The CommandStrategy interface.
 *
 * @param <T> type of command
 */
public interface CommandStrategy<T extends Command> {

    /**
     * @return the message class to react to.
     */
    Class<T> getMatchingClass();

    /**
     * Applies the strategy to the given command using the given context.
     *
     * @param context the context
     * @param command the command
     * @return the result of the strategy that will be handled in the context of the calling actor.
     */
    Result apply(Context context, T command);

    /**
     * @param context the context
     * @param command the command
     * @return {@code true} if the strategy is defined for the given command and can be applied.
     */
    boolean isDefined(Context context, T command);

    /**
     * The result of applying the strategy to the given command.
     */
    interface Result {

        /**
         * @param persistConsumer the consumer that is called if the result contains an event to persist and a response
         * @param notifyConsumer the consumer that is called for a response or an exception
         * @param becomeDeletedRunnable runnable that is called if the actor should now act as deleted handler
         */
        void apply(BiConsumer<ThingModifiedEvent, Consumer<ThingModifiedEvent>> persistConsumer,
                Consumer<WithDittoHeaders> notifyConsumer,
                Runnable becomeDeletedRunnable);

        /**
         * @return the empty result
         */
        static Result empty() {
            return ResultFactory.emptyResult();
        }
    }

    /**
     * The Context in which a strategy is executed.
     */
    interface Context {

        /**
         * @return the thing id
         */
        String getThingId();

        /**
         * @return the thing
         */
        Thing getThing();

        /**
         * @return the next revision
         */
        long getNextRevision();

        /**
         * @return the log
         */
        DiagnosticLoggingAdapter getLog();

        /**
         * @return the thing snapshotter
         */
        ThingSnapshotter<?, ?> getThingSnapshotter();
    }
}

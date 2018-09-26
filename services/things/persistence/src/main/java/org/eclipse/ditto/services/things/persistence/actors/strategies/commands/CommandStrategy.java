/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

import akka.event.DiagnosticLoggingAdapter;

/**
 * The CommandStrategy interface.
 *
 * @param <T> type of handled command.
 */
public interface CommandStrategy<T extends Command> {

    /**
     * @return the message class to react to.
     */
    Class<T> getMatchingClass();

    /**
     * Applies the strategy to the given command using the given context.
     *
     * @param context the context.
     * @param thing the current Thing of the ThingPersistenceActor.
     * @param nextRevision the next revision number of the ThingPersistenceActor.
     * @param command the command.
     * @return the result of the strategy that will be handled in the context of the calling actor.
     */
    Result apply(Context context, @Nullable Thing thing, long nextRevision, T command);

    /**
     * Indicates whether this strategy is defined for the specified command and can be applied.
     *
     * @param command the command.
     * @return {@code true} if the strategy is defined for the given command and can be applied.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    boolean isDefined(T command);

    /**
     * Indicates whether this strategy is defined for the specified command and context and can be applied.
     *
     * @param context the context.
     * @param thing the current Thing of the ThingPersistenceActor.
     * @param command the command.
     * @return {@code true} if the strategy is defined for the given command and can be applied.
     * @throws NullPointerException if any argument is {@code null}.
     */
    boolean isDefined(Context context, final Thing thing, T command);

    /**
     * The result of applying the strategy to the given command.
     */
    interface Result {

        /**
         * @param context the context
         * @param persistConsumer the consumer that is called if the result contains an event to persist and a response
         * @param notifyConsumer the consumer that is called for a response or an exception
         */
        void apply(final Context context,
                final BiConsumer<ThingModifiedEvent, BiConsumer<ThingModifiedEvent, Thing>> persistConsumer,
                final Consumer<WithDittoHeaders> notifyConsumer);

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
         * @return the thing ID.
         */
        String getThingId();

        /**
         * @return the log.
         */
        DiagnosticLoggingAdapter getLog();

        /**
         * @return the thing snapshotter.
         */
        ThingSnapshotter getThingSnapshotter();

        /**
         * @return the runnable to be called in case a Thing is created.
         */
        Runnable getBecomeCreatedRunnable();

        /**
         * @return the runnable to be called in case a Thing is deleted.
         */
        Runnable getBecomeDeletedRunnable();
    }

}

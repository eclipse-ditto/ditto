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
package org.eclipse.ditto.services.utils.persistentactors.commands;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.signals.commands.base.Command;

import akka.event.DiagnosticLoggingAdapter;

/**
 * The CommandStrategy interface.
 *
 * @param <T> type of handled command.
 * @param <S> type of entities.
 * @param <I> type of entity IDs.
 * @param <E> type of events.
 */
public interface CommandStrategy<T extends Command, S, I, E> {

    /**
     * @return the message class to react to.
     */
    Class<T> getMatchingClass();

    /**
     * Applies the strategy to the given command using the given context.
     *
     * @param context the context.
     * @param entity the current entity of the persistence actor.
     * @param nextRevision the next revision number of the entity.
     * @param command the command.
     * @return the result of the strategy that will be handled in the context of the calling actor.
     */
    Result<E> apply(Context<I> context, @Nullable S entity, long nextRevision, T command);

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
     * @param entity the current entity of the persistence actor.
     * @param command the command.
     * @return {@code true} if the strategy is defined for the given command and can be applied.
     * @throws NullPointerException if any argument is {@code null}.
     */
    boolean isDefined(Context<I> context, final S entity, T command);

    /**
     * The Context in which a strategy is executed.
     */
    interface Context<I> {

        /**
         * @return the thing ID.
         */
        I getEntityId();

        /**
         * @return the log.
         */
        DiagnosticLoggingAdapter getLog();

    }

}

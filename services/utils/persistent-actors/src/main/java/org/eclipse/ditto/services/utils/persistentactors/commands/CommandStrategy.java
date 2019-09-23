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
package org.eclipse.ditto.services.utils.persistentactors.commands;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.signals.commands.base.Command;

import akka.event.DiagnosticLoggingAdapter;

/**
 * The CommandStrategy interface.
 *
 * @param <C> the type of the handled command
 * @param <S> the type of the managed entity
 * @param <K> the type of the context
 * @param <R> the type of the results
 */
public interface CommandStrategy<C extends Command, S, K, R extends Result> {

    /**
     * @return the message class to react to.
     */
    Class<C> getMatchingClass();

    /**
     * Applies the strategy to the given command using the given context.
     *
     * @param context the context.
     * @param entity the current entity of the persistence actor.
     * @param nextRevision the next revision number of the entity.
     * @param command the command.
     * @return the result of the strategy that will be handled in the context of the calling actor.
     */
    R apply(Context<K> context, @Nullable S entity, long nextRevision, C command);

    /**
     * Indicates whether this strategy is defined for the specified command and can be applied.
     *
     * @param command the command.
     * @return {@code true} if the strategy is defined for the given command and can be applied.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    boolean isDefined(C command);

    /**
     * Indicates whether this strategy is defined for the specified command and context and can be applied.
     *
     * @param context the context.
     * @param entity the current entity of the persistence actor.
     * @param command the command.
     * @return {@code true} if the strategy is defined for the given command and can be applied.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default boolean isDefined(final Context<K> context, @Nullable final S entity, final C command) {
        return isDefined(command);
    }

    /**
     * The Context in which a strategy is executed.
     *
     * @param <K> the type of the state of the context
     */
    interface Context<K> {

        /**
         * @return the state.
         */
        K getState();

        /**
         * @return the log.
         */
        DiagnosticLoggingAdapter getLog();

    }

}

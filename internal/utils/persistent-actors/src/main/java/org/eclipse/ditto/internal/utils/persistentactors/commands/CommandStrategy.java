/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistentactors.commands;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;

import akka.actor.ActorSystem;

/**
 * The CommandStrategy interface.
 *
 * @param <C> the type of the handled command
 * @param <S> the type of the managed entity
 * @param <K> the type of the context
 * @param <E> the type of the events
 */
public interface CommandStrategy<C extends Command<?>, S, K, E extends Event<?>> {

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
    Result<E> apply(Context<K> context, @Nullable S entity, long nextRevision, C command);

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
     * Applies the strategy to the given command of unknown type using the given context.
     *
     * @param context the context.
     * @param entity the current entity of the persistence actor.
     * @param nextRevision the next revision number of the entity.
     * @param command the command of unknown type.
     * @return the result of the strategy if the strategy is defined for the command, or an empty optional otherwise.
     */
    default Optional<Result<E>> typeCheckAndApply(final Context<K> context, @Nullable final S entity,
            final long nextRevision,
            final Object command) {

        if (getMatchingClass().isInstance(command)) {
            final C theCommand = getMatchingClass().cast(command);
            if (isDefined(context, entity, theCommand)) {
                return Optional.of(apply(context, entity, nextRevision, theCommand));
            }
        }
        return Optional.empty();
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
        DittoDiagnosticLoggingAdapter getLog();

        /**
         * @return reference to actorSystem
         */
        ActorSystem getActorSystem();

    }

}

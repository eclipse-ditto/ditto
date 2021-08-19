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
package org.eclipse.ditto.internal.utils.persistentactors.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;

/**
 * This <em>Singleton</em> delegates a {@code Command} to a dedicated strategy - if one is available - to be handled.
 *
 * @param <C> the type of the handled command
 * @param <S> the type of the managed entity
 * @param <K> the type of the context
 * @param <E> the type of the result's event
 */
@Immutable
public abstract class AbstractCommandStrategies<C extends Command<?>, S, K, E extends Event<?>>
        extends AbstractCommandStrategy<C, S, K, E> {

    protected final Map<Class<? extends C>, CommandStrategy<? extends C, S, K, ? extends E>> strategies;

    /**
     * Constructs a new {@code AbstractCommandStrategy} object.
     *
     * @param theMatchingClass the class
     * @throws NullPointerException if {@code theMatchingClass} is {@code null}.
     */
    protected AbstractCommandStrategies(final Class<?> theMatchingClass) {
        super(theMatchingClass);
        strategies = new HashMap<>();
    }

    /**
     * @return the empty result.
     */
    protected abstract Result<E> getEmptyResult();

    /**
     * Add a command strategy. Call in constructor only.
     *
     * @param strategy the strategy.
     */
    protected void addStrategy(final CommandStrategy<? extends C, S, K, ? extends E> strategy) {
        final Class<? extends C> matchingClass = strategy.getMatchingClass();
        strategies.put(matchingClass, strategy);
    }

    @Override
    public Result<E> unhandled(final Context<K> context, @Nullable final S entity, final long nextRevision,
            final C command) {
        context.getLog().withCorrelationId(command)
                .info("Command <{}> cannot be handled by this strategy.", command);
        return getEmptyResult();
    }

    @Override
    public boolean isDefined(final C command) {
        return strategies.containsKey(command.getClass());
    }

    @Override
    public boolean isDefined(final Context<K> context, @Nullable final S entity, final C command) {
        return isDefined(command);
    }

    @Override
    protected Optional<Metadata> calculateRelativeMetadata(@Nullable final S entity, final C command) {
        return Optional.empty();
    }

    @Override
    protected Result<E> doApply(final Context<K> context, @Nullable final S entity, final long nextRevision,
            final C command, @Nullable final Metadata metadata) {

        final CommandStrategy<C, S, K, ? extends E> commandStrategy =
                getAppropriateStrategy(command.getClass());

        if (commandStrategy != null) {
            context.getLog().withCorrelationId(command)
                    .debug("Applying command <{}>", command);
            return commandStrategy.apply(context, entity, nextRevision, command).map(x -> x);
        } else {
            // this may happen when subclasses override the "isDefined" condition.
            return unhandled(context, entity, nextRevision, command);
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private CommandStrategy<C, S, K, ? extends E> getAppropriateStrategy(final Class<?> commandClass) {
        return (CommandStrategy<C, S, K, ? extends E>) strategies.get(commandClass);
    }

}

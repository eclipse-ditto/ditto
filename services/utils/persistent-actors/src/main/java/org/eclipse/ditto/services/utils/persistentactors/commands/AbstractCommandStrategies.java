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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.metadata.Metadata;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * This <em>Singleton</em> delegates a {@code Command} to a dedicated strategy - if one is available - to be handled.
 *
 * @param <C> the type of the handled command
 * @param <S> the type of the managed entity
 * @param <K> the type of the context
 * @param <R> the type of the results
 */
@Immutable
public abstract class AbstractCommandStrategies<C extends Command<?>, S, K, R extends Result<?>>
        extends AbstractCommandStrategy<C, S, K, R> {

    protected final Map<Class<? extends C>, CommandStrategy<? extends C, S, K, ? extends R>> strategies;

    /**
     * Constructs a new {@code AbstractCommandStrategy} object.
     *
     * @param theMatchingClass the class
     * @throws NullPointerException if {@code theMatchingClass} is {@code null}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected AbstractCommandStrategies(final Class theMatchingClass) {
        super((Class<C>) theMatchingClass);
        strategies = new HashMap<>();
    }

    /**
     * @return the empty result.
     */
    protected abstract R getEmptyResult();

    /**
     * Add a command strategy. Call in constructor only.
     *
     * @param strategy the strategy.
     */
    protected void addStrategy(final CommandStrategy<? extends C, S, K, ? extends R> strategy) {
        final Class<? extends C> matchingClass = strategy.getMatchingClass();
        strategies.put(matchingClass, strategy);
    }

    @Override
    public R unhandled(final Context<K> context, @Nullable final S entity, final long nextRevision, final C command) {
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
    protected R doApply(final Context<K> context, @Nullable final S entity, final long nextRevision, final C command,
            @Nullable final Metadata metadata) {

        final CommandStrategy<C, S, K, R> commandStrategy = getAppropriateStrategy(command.getClass());

        if (commandStrategy != null) {
            context.getLog().withCorrelationId(command)
                    .debug("Applying command <{}>", command);
            return commandStrategy.apply(context, entity, nextRevision, command);
        } else {
            // this may happen when subclasses override the "isDefined" condition.
            return unhandled(context, entity, nextRevision, command);
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private CommandStrategy<C, S, K, R> getAppropriateStrategy(final Class<?> commandClass) {
        return (CommandStrategy<C, S, K, R>) strategies.get(commandClass);
    }

    /**
     * Add command strategy in a covariant way.
     * TODO: Replace Result by event type; hard code the result type, then delete this method.
     *
     * @param strategies the command strategies.
     * @param strategy the strategy to add.
     * @param <E> type of events.
     * @param <C> type of commands.
     * @param <S> type of entities.
     * @param <K> type of contexts.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected static <E extends Event<?>, F extends E, C extends Command<?>, S, K> void addStrategy(
            final AbstractCommandStrategies<C, S, K, Result<E>> strategies,
            CommandStrategy<? extends C, S, K, Result<F>> strategy) {

        strategies.strategies.put(strategy.getMatchingClass(), (CommandStrategy) strategy);
    }

}

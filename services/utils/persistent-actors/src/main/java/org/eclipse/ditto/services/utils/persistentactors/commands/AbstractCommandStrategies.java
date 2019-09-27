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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.signals.commands.base.Command;

import akka.event.DiagnosticLoggingAdapter;

/**
 * This <em>Singleton</em> delegates a {@code Command} to a dedicated strategy - if one is available - to be handled.
 *
 * @param <C> the type of the handled command
 * @param <S> the type of the managed entity
 * @param <K> the type of the context
 * @param <R> the type of the results
 */
@Immutable
public abstract class AbstractCommandStrategies<C extends Command, S, K, R extends Result>
        extends AbstractCommandStrategy<C, S, K, R> {

    protected final Map<Class<? extends C>, CommandStrategy<? extends C, S, K, R>> strategies;

    /**
     * Constructs a new {@code AbstractCommandStrategy} object.
     *
     * @param theMatchingClass the class
     * @throws NullPointerException if {@code theMatchingClass} is {@code null}.
     */
    protected AbstractCommandStrategies(final Class<C> theMatchingClass) {
        super(theMatchingClass);
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
    protected void addStrategy(final CommandStrategy<? extends C, S, K, R> strategy) {
        final Class<? extends C> matchingClass = strategy.getMatchingClass();
        strategies.put(matchingClass, strategy);
    }

    @Override
    public R unhandled(final Context<K> context, @Nullable final S entity, final long nextRevision, final C command) {
        final DiagnosticLoggingAdapter log = context.getLog();
        LogUtil.enhanceLogWithCorrelationId(log, command);
        log.info("Command <{}> cannot be handled by this strategy.", command);
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
    protected R doApply(final Context<K> context, @Nullable final S entity, final long nextRevision, final C command) {

        final CommandStrategy<C, S, K, R> commandStrategy = getAppropriateStrategy(command.getClass());

        if (commandStrategy != null) {
            final DiagnosticLoggingAdapter log = context.getLog();
            LogUtil.enhanceLogWithCorrelationId(log, command);
            log.debug("Applying command <{}>", command);
            return commandStrategy.apply(context, entity, nextRevision, command);
        } else {
            // this may happen when subclasses override the "isDefined" condition.
            return unhandled(context, entity, nextRevision, command);
        }
    }

    @Nullable
    private CommandStrategy<C, S, K, R> getAppropriateStrategy(final Class commandClass) {
        return (CommandStrategy<C, S, K, R>) strategies.get(commandClass);
    }

}

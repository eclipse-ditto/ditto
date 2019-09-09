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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.Entity;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;

import akka.event.DiagnosticLoggingAdapter;

/**
 * This <em>Singleton</em> delegates a {@code Command} to a dedicated strategy - if one is available - to be handled.
 */
@Immutable
public abstract class AbstractReceiveStrategy<S extends Entity, E> extends AbstractCommandStrategy<Command, S, E> {

    protected final Map<Class<? extends Command>, CommandStrategy<? extends Command, S, E>> strategies =
            new HashMap<>();

    /**
     * Constructs a new {@code AbstractCommandStrategy} object.
     *
     * @param theMatchingClass the class
     * @throws NullPointerException if {@code theMatchingClass} is {@code null}.
     */
    protected AbstractReceiveStrategy(
            final Class<Command> theMatchingClass) {
        super(theMatchingClass);
    }

    /**
     * TODO
     *
     * @param strategy the strategy.
     */
    public void addStrategy(final CommandStrategy<? extends Command, S, E> strategy) {
        final Class<? extends Command> matchingClass = strategy.getMatchingClass();
        strategies.put(matchingClass, strategy);
    }

    @Override
    protected Result<E> unhandled(final Context context, final S entity, final long nextRevision,
            final Command command) {
        final DiagnosticLoggingAdapter log = context.getLog();
        LogUtil.enhanceLogWithCorrelationId(log, command);
        log.info("Command of type <{}> cannot be handled by this strategy.", command.getClass().getName());

        return ResultFactory.emptyResult();
    }

    @Override
    public boolean isDefined(final Command command) {
        return strategies.containsKey(command.getClass());
    }

    @Override
    public boolean isDefined(final Context context, @Nullable final S entity, final Command command) {
        return isDefined(command);
    }

    @Override
    protected Result<E> doApply(final Context context, @Nullable final S entity, final long nextRevision,
            final Command command) {
        final CommandStrategy<Command, S, E> commandStrategy = getAppropriateStrategy(command.getClass());

        final DiagnosticLoggingAdapter log = context.getLog();
        LogUtil.enhanceLogWithCorrelationId(log, command);
        LogUtil.enhanceLogWithCorrelationId(log, command.getDittoHeaders().getCorrelationId());
        if (log.isDebugEnabled()) {
            log.debug("Applying command <{}>: {}", command.getType(), command.toJsonString());
        }
        return commandStrategy.apply(context, entity, nextRevision, command);
    }

    @SuppressWarnings("unchecked")
    private CommandStrategy<Command, S, E> getAppropriateStrategy(final Class commandClass) {
        return (CommandStrategy<Command, S, E>) strategies.get(commandClass);
    }

}

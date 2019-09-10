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

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.event.DiagnosticLoggingAdapter;

/**
 * This <em>Singleton</em> delegates a {@code Command} to a dedicated strategy - if one is available - to be handled.
 */
@Immutable
public abstract class AbstractReceiveStrategy<C, S, I, R> extends AbstractCommandStrategy<C, S, I, R> {

    protected final Map<Class<? extends C>, CommandStrategy<? extends C, S, I, R>> strategies = new HashMap<>();

    /**
     * Constructs a new {@code AbstractCommandStrategy} object.
     *
     * @param theMatchingClass the class
     * @throws NullPointerException if {@code theMatchingClass} is {@code null}.
     */
    protected AbstractReceiveStrategy(
            final Class<C> theMatchingClass) {
        super(theMatchingClass);
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
    protected void addStrategy(final CommandStrategy<? extends C, S, I, R> strategy) {
        final Class<? extends C> matchingClass = strategy.getMatchingClass();
        strategies.put(matchingClass, strategy);
    }

    @Override
    public R unhandled(final Context<I> context, @Nullable final S entity, final long nextRevision, final C command) {
        final DiagnosticLoggingAdapter log = context.getLog();
        if (command instanceof WithDittoHeaders) {
            LogUtil.enhanceLogWithCorrelationId(log, (WithDittoHeaders) command);
        }
        log.info("Command <{}> cannot be handled by this strategy.", command);
        return getEmptyResult();
    }

    @Override
    public boolean isDefined(final C command) {
        return strategies.containsKey(command.getClass());
    }

    @Override
    public boolean isDefined(final Context<I> context, @Nullable final S entity, final C command) {
        return isDefined(command);
    }

    @Override
    protected R doApply(final Context<I> context, @Nullable final S entity, final long nextRevision, final C command) {

        final CommandStrategy<C, S, I, R> commandStrategy = getAppropriateStrategy(command.getClass());

        final DiagnosticLoggingAdapter log = context.getLog();
        if (command instanceof WithDittoHeaders) {
            LogUtil.enhanceLogWithCorrelationId(log, (WithDittoHeaders) command);
        }
        if (log.isDebugEnabled()) {
            log.debug("Applying command <{}>", command);
        }
        return commandStrategy.apply(context, entity, nextRevision, command);
    }

    @SuppressWarnings("unchecked")
    private CommandStrategy<C, S, I, R> getAppropriateStrategy(final Class commandClass) {
        return (CommandStrategy<C, S, I, R>) strategies.get(commandClass);
    }

}

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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Abstract base implementation of {@code CommandStrategy}.
 *
 * @param <T> type of the handled command.
 */
@Immutable
abstract class AbstractCommandStrategy<T extends Command> implements CommandStrategy<T> {

    private final Class<T> matchingClass;

    /**
     * Constructs a new {@code AbstractCommandStrategy} object.
     *
     * @param theMatchingClass the class
     * @throws NullPointerException if {@code theMatchingClass} is {@code null}.
     */
    protected AbstractCommandStrategy(final Class<T> theMatchingClass) {
        matchingClass = checkNotNull(theMatchingClass, "matching Class");
    }

    @Override
    public Result apply(final Context context, final T command) {
        checkNotNull(context, "Context");
        checkNotNull(command, "Command");

        if (isDefined(context, command)) {
            final DiagnosticLoggingAdapter logger = context.getLog();
            LogUtil.enhanceLogWithCorrelationId(logger, command.getDittoHeaders().getCorrelationId());
            if (logger.isDebugEnabled()) {
                logger.debug("Applying command <{}>: {}", command.getType(), command.toJsonString());
            }
            return doApply(context, command);
        } else {
            return unhandled(context, command);
        }
    }

    protected abstract Result doApply(final Context context, final T command);

    protected Result unhandled(final Context context, final T command) {
        throw ExceptionFactory.unhandled(command);
    }

    @Override
    public Class<T> getMatchingClass() {
        return matchingClass;
    }

    @Override
    public boolean isDefined(final T command) {
        throw new UnsupportedOperationException("This method is not supported by this implementation.");
    }

    @Override
    public boolean isDefined(final Context context, final T command) {
        checkNotNull(context, "Context");
        checkNotNull(command, "Command");

        return context.getThing()
                .flatMap(Thing::getId)
                .filter(thingId -> Objects.equals(thingId, command.getId()))
                .isPresent();
    }

    protected static boolean isThingDeleted(@Nullable final Thing thing) {
        return null == thing || thing.hasLifecycle(ThingLifecycle.DELETED);
    }

    protected static Instant getEventTimestamp() {
        return Instant.now();
    }

}

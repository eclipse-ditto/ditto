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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.Entity;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Abstract base implementation of {@code CommandStrategy}.
 *
 * @param <T> type of the handled command.
 */
@Immutable
public abstract class AbstractCommandStrategy<T extends Command, S extends Entity, E>
        implements CommandStrategy<T, S, E> {

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
    public Result<E> apply(final Context context, @Nullable final S entity, final long nextRevision, final T command) {
        checkNotNull(context, "Context");
        checkNotNull(command, "Command");

        if (isDefined(context, entity, command)) {
            final DiagnosticLoggingAdapter logger = context.getLog();
            LogUtil.enhanceLogWithCorrelationId(logger, command.getDittoHeaders().getCorrelationId());
            if (logger.isDebugEnabled()) {
                logger.debug("Applying command <{}>: {}", command.getType(), command.toJsonString());
            }
            return doApply(context, entity, nextRevision, command);
        } else {
            return unhandled(context, entity, nextRevision, command);
        }
    }

    protected abstract Result<E> doApply(final Context context, @Nullable final S entity, final long nextRevision,
            final T command);

    protected Result<E> unhandled(final Context context, @Nullable final S entity, final long nextRevision,
            final T command) {
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
    public boolean isDefined(final Context context, @Nullable final S entity, final T command) {
        checkNotNull(context, "Context");
        checkNotNull(command, "Command");

        return Optional.ofNullable(entity)
                .flatMap(Entity::getEntityId)
                .filter(thingId -> Objects.equals(thingId, command.getEntityId()))
                .isPresent();
    }

    // TODO: check usage
    protected static boolean isThingDeleted(@Nullable final Thing thing) {
        return null == thing || thing.hasLifecycle(ThingLifecycle.DELETED);
    }

    protected static <S> S getEntityOrThrow(@Nullable final S entity) {
        if (null != entity) {
            return entity;
        }
        throw new NoSuchElementException("This Context does not have an entity!");
    }

    protected static Instant getEventTimestamp() {
        return Instant.now();
    }

}

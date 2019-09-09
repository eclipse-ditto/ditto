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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.Entity;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.signals.commands.base.Command;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Abstract base implementation of {@code CommandStrategy}.
 *
 * @param <T> type of the handled command.
 */
@Immutable
public abstract class AbstractCommandStrategy<T extends Command, S extends Entity, I, E>
        implements CommandStrategy<T, S, I, E> {

    private final Class<T> matchingClass;

    /**
     * Constructs a new {@code AbstractCommandStrategy} object.
     *
     * @param theMatchingClass the class.
     * @throws NullPointerException if {@code theMatchingClass} is {@code null}.
     */
    protected AbstractCommandStrategy(final Class<T> theMatchingClass) {
        matchingClass = checkNotNull(theMatchingClass, "matching Class");
    }

    @Override
    public Result<E> apply(final Context<I> context, @Nullable final S entity, final long nextRevision,
            final T command) {
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

    /**
     * Execute a command strategy after it is determined applicable.
     *
     * @param context context of the persistent actor.
     * @param entity entity of the persistent actor.
     * @param nextRevision the next revision to allocate to events.
     * @param command the incoming command.
     * @return result of the command strategy.
     */
    protected abstract Result<E> doApply(final Context<I> context, @Nullable final S entity, final long nextRevision,
            final T command);

    /**
     * Throw an {@code IllegalArgumentException} for unhandled command.
     *
     * @param context context of the persistent actor.
     * @param entity entity of the persistent actor.
     * @param nextRevision the next revision.
     * @param command the unhandled command
     * @return nothing.
     * @throws java.lang.IllegalArgumentException always.
     */
    public Result<E> unhandled(final Context<I> context, @Nullable final S entity, final long nextRevision,
            final T command) {
        final String msgPattern = "This Thing Actor did not handle the requested Thing with ID <{0}>!";
        throw new IllegalArgumentException(MessageFormat.format(msgPattern, command.getEntityId()));
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
    public boolean isDefined(final Context<I> context, @Nullable final S entity, final T command) {
        checkNotNull(context, "Context");
        checkNotNull(command, "Command");

        return Optional.ofNullable(entity)
                .flatMap(Entity::getEntityId)
                .filter(thingId -> Objects.equals(thingId, command.getEntityId()))
                .isPresent();
    }

    /**
     * Perform a null check on the entity and return it.
     *
     * @param entity the entity.
     * @param <S> type of the entity.
     * @return the entity if it is not null.
     * @throws java.util.NoSuchElementException if the entity is null.
     */
    protected static <S> S getEntityOrThrow(@Nullable final S entity) {
        if (null != entity) {
            return entity;
        }
        throw new NoSuchElementException("This Context does not have an entity!");
    }

    /**
     * Get the current timestamp for an event.
     *
     * @return the current timestamp.
     */
    protected static Instant getEventTimestamp() {
        return Instant.now();
    }

}

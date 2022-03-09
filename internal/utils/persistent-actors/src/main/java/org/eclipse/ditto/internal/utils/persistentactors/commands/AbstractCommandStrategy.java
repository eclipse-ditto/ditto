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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;

/**
 * Abstract base implementation of {@code CommandStrategy}.
 *
 * @param <C> the type of the handled command
 * @param <S> the type of the managed entity
 * @param <K> the type of the context
 * @param <E> the type of the result's event
 */
@Immutable
public abstract class AbstractCommandStrategy<C extends Command<?>, S, K, E extends Event<?>>
        implements CommandStrategy<C, S, K, E> {

    private final Class<C> matchingClass;

    /**
     * Constructs a new {@code AbstractCommandStrategy} object.
     *
     * @param theMatchingClass the class.
     * @throws NullPointerException if {@code theMatchingClass} is {@code null}.
     */
    @SuppressWarnings("unchecked")
    protected AbstractCommandStrategy(final Class<?> theMatchingClass) {
        matchingClass = (Class<C>) checkNotNull(theMatchingClass, "matching Class");
    }

    @Override
    public Result<E> apply(final Context<K> context, @Nullable final S entity, final long nextRevision,
            final C command) {
        checkNotNull(context, "Context");
        checkNotNull(command, "Command");

        if (isDefined(context, entity, command)) {
            if (context.getLog().isDebugEnabled()) {
                context.getLog().withCorrelationId(command)
                        .debug("Applying command in <{}>: <{}>", getClass().getSimpleName(), command);
            }
            @Nullable final Metadata metadata = calculateRelativeMetadata(entity, command).orElse(null);
            return doApply(context, entity, nextRevision, command, metadata);
        } else {
            return unhandled(context, entity, nextRevision, command);
        }
    }

    /**
     * Calculates the metadata based on the passed {@code command}.
     *
     * @param entity entity of the persistent actor.
     * @param command the incoming command.
     * @return the optionally calculated metadata for the passed command.
     */
    protected abstract Optional<Metadata> calculateRelativeMetadata(@Nullable S entity, C command);

    /**
     * Execute a command strategy after it is determined applicable.
     *
     * @param context context of the persistent actor.
     * @param entity entity of the persistent actor.
     * @param nextRevision the next revision to allocate to events.
     * @param command the incoming command.
     * @param metadata the metadata extracted from the incoming command.
     * @return result of the command strategy.
     */
    protected abstract Result<E> doApply(Context<K> context, @Nullable S entity, long nextRevision, C command,
            @Nullable Metadata metadata);

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
    public Result<E> unhandled(final Context<K> context, @Nullable final S entity, final long nextRevision,
            final C command) {
        final String msgPattern = "Unhandled: <{0}>!";
        throw new IllegalArgumentException(MessageFormat.format(msgPattern, command));
    }

    @Override
    public Class<C> getMatchingClass() {
        return matchingClass;
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

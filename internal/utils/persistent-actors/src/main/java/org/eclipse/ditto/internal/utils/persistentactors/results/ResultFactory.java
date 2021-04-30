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
package org.eclipse.ditto.internal.utils.persistentactors.results;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.events.Event;

/**
 * A factory for creating {@link Result} instances.
 */
@Immutable
public final class ResultFactory {

    private ResultFactory() {
        throw new AssertionError();
    }

    /**
     * Create a mutation result.
     *
     * @param command command that caused the mutation.
     * @param eventToPersist event of the mutation.
     * @param response response of the command.
     * @param <E> type of the event.
     * @return the result.
     */
    public static <E extends Event<?>> Result<E> newMutationResult(final Command<?> command, final E eventToPersist,
            final WithDittoHeaders response) {

        return new MutationResult<>(command, eventToPersist, response, false, false);
    }

    /**
     * Create a mutation result.
     *
     * @param command command that caused the mutation.
     * @param eventToPersist event of the mutation.
     * @param response response of the command.
     * @param becomeCreated whether the actor should behave as if the entity is created.
     * @param becomeDeleted whether the actor should behave as if the entity is deleted.
     * @param <E> type of the event.
     * @return the result.
     */
    public static <E extends Event<?>> Result<E> newMutationResult(final Command<?> command,
            final E eventToPersist,
            final WithDittoHeaders response,
            final boolean becomeCreated,
            final boolean becomeDeleted) {

        return new MutationResult<>(command, eventToPersist, response, becomeCreated, becomeDeleted);
    }

    /**
     * Create an error result.
     *
     * @param dittoRuntimeException the error.
     * @param errorCausingCommand the command that caused the error.
     * @param <E> type of events (irrelevant).
     * @return the result.
     */
    public static <E extends Event<?>> Result<E> newErrorResult(final DittoRuntimeException dittoRuntimeException,
            final Command<?> errorCausingCommand) {
        return new ErrorResult<>(dittoRuntimeException, errorCausingCommand);
    }

    /**
     * Create a query result.
     *
     * @param command the query command.
     * @param response the response.
     * @param <E> type of events (irrelevant).
     * @return the result.
     */
    public static <E extends Event<?>> Result<E> newQueryResult(final Command<?> command,
            final WithDittoHeaders response) {
        return new QueryResult<>(command, response);
    }

    /**
     * Get the empty result.
     *
     * @param <E> type of events (irrelevant).
     * @return the empty result.
     */
    public static <E extends Event<?>> Result<E> emptyResult() {
        return EmptyResult.getInstance();
    }


}

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

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.events.Event;

/**
 * Evaluator of results of command strategies.
 *
 * @param <E> type of events.
 */
public interface ResultVisitor<E extends Event<?>> {

    /**
     * Evaluate the empty result. Do nothing by default.
     */
    default void onEmpty() {
        // do nothing on empty result by default
    }

    /**
     * Evaluate a mutation result.
     *
     * @param command command that caused the mutation.
     * @param event event of the mutation.
     * @param response response of the command.
     * @param becomeCreated whether the actor should behave as if the entity is created.
     * @param becomeDeleted whether the actor should behave as if the entity is deleted.
     */
    void onMutation(Command<?> command, E event, WithDittoHeaders response, boolean becomeCreated,
            boolean becomeDeleted);

    /**
     * Evaluate a query result.
     *
     * @param command the query command.
     * @param response the response.
     */
    void onQuery(Command<?> command, WithDittoHeaders response);

    /**
     * Evaluate an error result.
     *
     * @param error the error.
     */
    void onError(DittoRuntimeException error, Command<?> errorCausingCommand);
}

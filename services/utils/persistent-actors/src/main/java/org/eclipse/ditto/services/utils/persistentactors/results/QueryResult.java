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
package org.eclipse.ditto.services.utils.persistentactors.results;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * Result for query commands.
 *
 * @param <E> type of events (irrelevant).
 */
public final class QueryResult<E extends Event> implements Result<E> {

    private final Command command;
    private final WithDittoHeaders response;

    QueryResult(final Command command, final WithDittoHeaders response) {
        this.command = command;
        this.response = response;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " [" +
                "command=" + command +
                ", response=" + response +
                ']';
    }

    @Override
    public void accept(final ResultVisitor<E> visitor) {
        visitor.onQuery(command, response);
    }
}

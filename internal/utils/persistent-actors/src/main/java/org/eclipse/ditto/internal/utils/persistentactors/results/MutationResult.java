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

import java.util.function.Function;

import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.events.Event;

/**
 * Result that demands persistence of a mutation event.
 *
 * @param <E> type of the event.
 */
public final class MutationResult<E extends Event<?>> implements Result<E> {

    private final Command<?> command;
    private final E eventToPersist;
    private final WithDittoHeaders response;
    private final boolean becomeCreated;
    private final boolean becomeDeleted;

    MutationResult(final Command<?> command, final E eventToPersist, final WithDittoHeaders response,
            final boolean becomeCreated, final boolean becomeDeleted) {
        this.command = command;
        this.eventToPersist = eventToPersist;
        this.response = response;
        this.becomeCreated = becomeCreated;
        this.becomeDeleted = becomeDeleted;
    }

    @Override
    public void accept(final ResultVisitor<E> visitor) {
        visitor.onMutation(command, eventToPersist, response, becomeCreated, becomeDeleted);
    }

    @Override
    public <F extends Event<?>> Result<F> map(final Function<E, F> mappingFunction) {
        return new MutationResult<>(command, mappingFunction.apply(eventToPersist), response, becomeCreated,
                becomeDeleted);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " [" +
                "command=" + command +
                ", eventToPersist=" + eventToPersist +
                ", response=" + response +
                ", becomeCreated=" + becomeCreated +
                ", becomeDeleted=" + becomeDeleted +
                ']';
    }
}

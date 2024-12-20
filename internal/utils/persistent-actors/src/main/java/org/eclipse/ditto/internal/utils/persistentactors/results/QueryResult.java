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

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.internal.utils.tracing.span.StartedSpan;

/**
 * Result for query commands.
 *
 * @param <E> type of events (irrelevant).
 */
public final class QueryResult<E extends Event<?>> implements Result<E> {

    private final Command<?> command;
    @Nullable private final WithDittoHeaders response;
    @Nullable private final CompletionStage<WithDittoHeaders> responseStage;

    QueryResult(final Command<?> command,
            @Nullable final WithDittoHeaders response,
            @Nullable final CompletionStage<WithDittoHeaders> responseStage) {
        this.command = command;
        this.response = response;
        this.responseStage = responseStage;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " [" +
                "command=" + command +
                ", response=" + response +
                ", responseStage=" + responseStage +
                ']';
    }

    @Override
    public void accept(final ResultVisitor<E> visitor, @Nullable final StartedSpan startedSpan) {
        if (responseStage != null) {
            visitor.onStagedQuery(command, responseStage, startedSpan);
        } else {
            visitor.onQuery(command, response);
            if (startedSpan != null) {
                startedSpan.finish();
            }
        }
    }

    @Override
    public <F extends Event<?>> Result<F> map(final Function<E, F> mappingFunction) {
        return new QueryResult<>(command, response, null);
    }

    @Override
    public <F extends Event<?>> Result<F> mapStages(final Function<CompletionStage<E>, CompletionStage<F>> mappingFunction) {
        return new QueryResult<>(command, null, responseStage);
    }
}

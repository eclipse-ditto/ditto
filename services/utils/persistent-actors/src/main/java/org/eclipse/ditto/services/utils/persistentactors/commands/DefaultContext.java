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

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Holds the context required to execute the
 * {@link org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy}s.
 */
@Immutable
public final class DefaultContext<I> implements CommandStrategy.Context<I> {

    private final I state;
    private final DiagnosticLoggingAdapter log;

    private DefaultContext(final I state, final DiagnosticLoggingAdapter theLog) {

        this.state = checkNotNull(state, "Thing ID");
        log = checkNotNull(theLog, "DiagnosticLoggingAdapter");
    }

    /**
     * Returns an instance of {@code DefaultContext}.
     *
     * @param thingId the ID of the Thing.
     * @param log the logging adapter to be used.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static <I> DefaultContext<I> getInstance(final I thingId, final DiagnosticLoggingAdapter log) {
        return new DefaultContext<>(thingId, log);
    }

    @Override
    public I getState() {
        return state;
    }

    @Override
    public DiagnosticLoggingAdapter getLog() {
        return log;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultContext that = (DefaultContext) o;
        return Objects.equals(state, that.state) && Objects.equals(log, that.log);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, log);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "thingId=" + state +
                ", log=" + log +
                "]";
    }

}

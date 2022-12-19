/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;

import akka.actor.ActorSystem;

/**
 * Holds the context required to execute the
 * {@link CommandStrategy}s.
 *
 * @param <K> the type of the context's state
 */
@Immutable
public final class DefaultContext<K> implements CommandStrategy.Context<K> {

    private final K state;
    private final DittoDiagnosticLoggingAdapter log;

    private final ActorSystem actorSystem;

    private DefaultContext(final K state, final DittoDiagnosticLoggingAdapter log, final ActorSystem actorSystem) {

        this.state = checkNotNull(state, "state");
        this.log = checkNotNull(log, "log");
        this.actorSystem = checkNotNull(actorSystem, "actorSystem");
    }

    /**
     * Returns an instance of {@code DefaultContext}.
     *
     * @param state the state.
     * @param log the logging adapter to be used.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static <K> DefaultContext<K> getInstance(final K state, final DittoDiagnosticLoggingAdapter log,
            final ActorSystem actorSystem) {
        return new DefaultContext<>(state, log, actorSystem);
    }

    @Override
    public K getState() {
        return state;
    }

    @Override
    public DittoDiagnosticLoggingAdapter getLog() {
        return log;
    }

    @Override
    public ActorSystem getActorSystem() {
        return actorSystem;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultContext<?> that = (DefaultContext<?>) o;
        return Objects.equals(state, that.state)
                && Objects.equals(log, that.log)
                && Objects.equals(actorSystem, that.actorSystem);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, log, actorSystem);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "state=" + state +
                ", log=" + log +
                ", actorSystem=" + actorSystem +
                "]";
    }

}

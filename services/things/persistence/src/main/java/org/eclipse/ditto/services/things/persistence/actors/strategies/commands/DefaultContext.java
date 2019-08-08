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

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.things.id.ThingId;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Holds the context required to execute the {@link CommandStrategy}s.
 */
@Immutable
public final class DefaultContext implements CommandStrategy.Context {

    private final ThingId thingId;
    private final DiagnosticLoggingAdapter log;
    private final Runnable becomeCreatedRunnable;
    private final Runnable becomeDeletedRunnable;

    private DefaultContext(final ThingId theThingId,
            final DiagnosticLoggingAdapter theLog,
            final Runnable becomeCreatedRunnable,
            final Runnable becomeDeletedRunnable) {

        thingId = checkNotNull(theThingId, "Thing ID");
        log = checkNotNull(theLog, "DiagnosticLoggingAdapter");
        this.becomeCreatedRunnable = checkNotNull(becomeCreatedRunnable, "becomeCreatedRunnable");
        this.becomeDeletedRunnable = checkNotNull(becomeDeletedRunnable, "becomeDeletedRunnable");
    }

    /**
     * Returns an instance of {@code DefaultContext}.
     *
     * @param thingId the ID of the Thing.
     * @param log the logging adapter to be used.
     * @param becomeCreatedRunnable the runnable to be called in case a Thing is created.
     * @param becomeDeletedRunnable the runnable to be called in case a Thing is deleted.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DefaultContext getInstance(final ThingId thingId,
            final DiagnosticLoggingAdapter log,
            final Runnable becomeCreatedRunnable,
            final Runnable becomeDeletedRunnable) {

        return new DefaultContext(thingId, log, becomeCreatedRunnable, becomeDeletedRunnable);
    }

    @Override
    public ThingId getThingEntityId() {
        return thingId;
    }

    @Override
    public DiagnosticLoggingAdapter getLog() {
        return log;
    }

    @Override
    public Runnable getBecomeCreatedRunnable() {
        return becomeCreatedRunnable;
    }

    @Override
    public Runnable getBecomeDeletedRunnable() {
        return becomeDeletedRunnable;
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
        return Objects.equals(thingId, that.thingId) &&
                Objects.equals(log, that.log) &&
                Objects.equals(becomeCreatedRunnable, that.becomeCreatedRunnable) &&
                Objects.equals(becomeDeletedRunnable, that.becomeDeletedRunnable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(thingId, log, becomeCreatedRunnable, becomeDeletedRunnable);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "thingId=" + thingId +
                ", log=" + log +
                ", becomeCreatedRunnable=" + becomeCreatedRunnable +
                ", becomeDeletedRunnable=" + becomeDeletedRunnable +
                "]";
    }

}

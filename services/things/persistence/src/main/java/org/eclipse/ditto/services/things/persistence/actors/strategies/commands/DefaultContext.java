/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Holds the context required to execute the {@link CommandStrategy}s.
 */
@Immutable
public final class DefaultContext implements CommandStrategy.Context {

    private final String thingId;
    @Nullable private final Thing thing;
    private final long nextRevision;
    private final DiagnosticLoggingAdapter log;
    private final ThingSnapshotter<?, ?> thingSnapshotter;

    private DefaultContext(final String theThingId,
            @Nullable final Thing theThing,
            final long theNextRevision,
            final DiagnosticLoggingAdapter theLog,
            final ThingSnapshotter<?, ?> theThingSnapshotter) {

        thingId = checkNotNull(theThingId, "Thing ID");
        thing = theThing;
        nextRevision = theNextRevision;
        log = checkNotNull(theLog, "DiagnosticLoggingAdapter");
        thingSnapshotter = checkNotNull(theThingSnapshotter, "ThingSnapshotter");
    }

    /**
     * Returns an instance of {@code DefaultContext}.
     *
     * @param thingId the ID of the Thing.
     * @param thing the Thing or {@code null}.
     * @param nextRevision the next revision number.
     * @param log the logging adapter to be used.
     * @param thingSnapshotter the snapshotter to be used.
     * @return the instance.
     * @throws NullPointerException if any argument but {@code thing} is {@code null}.
     */
    public static DefaultContext getInstance(final String thingId,
            @Nullable final Thing thing,
            final long nextRevision,
            final DiagnosticLoggingAdapter log,
            final ThingSnapshotter<?, ?> thingSnapshotter) {

        return new DefaultContext(thingId, thing, nextRevision, log, thingSnapshotter);
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    @Override
    public Thing getThingOrThrow() {
        if (null != thing) {
            return thing;
        }
        throw new NoSuchElementException("This Context does not have a Thing!");
    }

    @Override
    public Optional<Thing> getThing() {
        return Optional.ofNullable(thing);
    }

    @Override
    public long getNextRevision() {
        return nextRevision;
    }

    @Override
    public DiagnosticLoggingAdapter getLog() {
        return log;
    }

    @Override
    public ThingSnapshotter<?, ?> getThingSnapshotter() {
        return thingSnapshotter;
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
        return nextRevision == that.nextRevision &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(thing, that.thing) &&
                Objects.equals(log, that.log) &&
                Objects.equals(thingSnapshotter, that.thingSnapshotter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(thingId, thing, nextRevision, log, thingSnapshotter);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "thingId=" + thingId +
                ", thing=" + thing +
                ", nextRevision=" + nextRevision +
                ", log=" + log +
                ", thingSnapshotter=" + thingSnapshotter +
                "]";
    }

}

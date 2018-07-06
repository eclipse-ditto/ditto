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
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;

import akka.event.DiagnosticLoggingAdapter;

public class ImmutableContext implements CommandStrategy.Context {

    private final String thingId;
    private final Thing thing;
    private final long nextRevision;
    private final DiagnosticLoggingAdapter log;
    private final ThingSnapshotter<?, ?> thingSnapshotter;

    public ImmutableContext(final String thingId, final Thing thing, final long nextRevision,
            final DiagnosticLoggingAdapter log,
            final ThingSnapshotter<?, ?> thingSnapshotter) {
        this.thingId = thingId;
        this.thing = thing;
        this.nextRevision = nextRevision;
        this.log = log;
        this.thingSnapshotter = thingSnapshotter;
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    @Override
    public Thing getThing() {
        return thing;
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

}

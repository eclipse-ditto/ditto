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

import akka.event.DiagnosticLoggingAdapter;

public class ImmutableContext implements ReceiveStrategy.Context {

    public final String thingId;
    public final Thing thing;
    public final long nextRevision;
    public final DiagnosticLoggingAdapter log;

    public ImmutableContext(final String thingId, final Thing thing, final long nextRevision,
            final DiagnosticLoggingAdapter log) {
        this.thingId = thingId;
        this.thing = thing;
        this.nextRevision = nextRevision;
        this.log = log;
    }

    @Override
    public String getThingId() {
        return null;
    }

    @Override
    public Thing getThing() {
        return null;
    }

    @Override
    public long nextRevision() {
        return 0;
    }

    @Override
    public DiagnosticLoggingAdapter log() {
        return null;
    }
}

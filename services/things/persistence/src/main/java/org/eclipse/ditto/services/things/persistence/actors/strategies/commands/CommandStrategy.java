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

import java.util.Optional;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

import akka.event.DiagnosticLoggingAdapter;

public interface CommandStrategy<T extends Command> {

    Class<T> getMatchingClass();

    Result apply(Context context, T command);

    boolean isDefined(Context context, T command);

    interface Result {

        Optional<ThingModifiedEvent> getEventToPersist();

        Optional<WithDittoHeaders> getResponse();

        Optional<DittoRuntimeException> getException();

        boolean isBecomeDeleted();

        static Result empty() {
            return ResultFactory.emptyResult();
        }
    }

    interface Context {

        String getThingId();

        Thing getThing();

        long getNextRevision();

        DiagnosticLoggingAdapter getLog();

        ThingSnapshotter<?, ?> getThingSnapshotter();
    }
}

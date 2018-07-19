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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingResponse;
import org.eclipse.ditto.signals.events.things.ThingDeleted;

/**
 * This strategy handles the {@link DeleteThing} command.
 */
@Immutable
final class DeleteThingStrategy extends AbstractCommandStrategy<DeleteThing> {

    /**
     * Constructs a new {@code DeleteThingStrategy} object.
     */
    DeleteThingStrategy() {
        super(DeleteThing.class);
    }

    @Override
    protected Result doApply(final Context context, final DeleteThing command) {
        final String thingId = context.getThingId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        context.getLog().info("Deleted Thing with ID <{}>.", thingId);

        return ResultFactory.newResult(
                ThingDeleted.of(thingId, context.getNextRevision(), getEventTimestamp(), dittoHeaders),
                DeleteThingResponse.of(thingId, dittoHeaders), true);
    }

}

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

import static org.eclipse.ditto.services.things.persistence.actors.strategies.commands.ResultFactory.newResult;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributes;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.events.things.AttributesCreated;
import org.eclipse.ditto.signals.events.things.AttributesModified;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

/**
 * This strategy handles the {@link ModifyAttributes} command.
 */
@ThreadSafe
public final class ModifyAttributesStrategy extends AbstractCommandStrategy<ModifyAttributes> {

    /**
     * Constructs a new {@code ModifyAttributesStrategy} object.
     */
    ModifyAttributesStrategy() {
        super(ModifyAttributes.class);
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context, final ModifyAttributes command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final ThingModifiedEvent eventToPersist;
        final ThingModifyCommandResponse response;

        final String thingId = context.getThingId();
        final Thing thing = context.getThing();
        final long nextRevision = context.getNextRevision();

        if (thing.getAttributes().isPresent()) {
            eventToPersist = AttributesModified.of(thingId, command.getAttributes(), nextRevision,
                    eventTimestamp(), dittoHeaders);
            response = ModifyAttributesResponse.modified(thingId, dittoHeaders);
        } else {
            eventToPersist = AttributesCreated.of(thingId, command.getAttributes(), nextRevision,
                    eventTimestamp(), dittoHeaders);
            response = ModifyAttributesResponse.created(thingId, command.getAttributes(), dittoHeaders);
        }

        return newResult(eventToPersist, response);
    }

}

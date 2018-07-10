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

import java.util.Optional;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributeResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.events.things.AttributeCreated;
import org.eclipse.ditto.signals.events.things.AttributeModified;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

/**
 * This strategy handles the {@link ModifyAttribute} command.
 */
@ThreadSafe
final class ModifyAttributeStrategy extends AbstractCommandStrategy<ModifyAttribute> {

    /**
     * Constructs a new {@code ModifyAttributeStrategy} object.
     */
    ModifyAttributeStrategy() {
        super(ModifyAttribute.class);
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context, final ModifyAttribute command) {
        final String thingId = context.getThingId();
        final Thing thing = context.getThing();
        final long nextRevision = context.getNextRevision();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Optional<Attributes> optionalAttributes = thing.getAttributes();

        final ThingModifiedEvent eventToPersist;
        final ThingModifyCommandResponse response;

        final JsonPointer attributeJsonPointer = command.getAttributePointer();
        final JsonValue attributeValue = command.getAttributeValue();
        if (optionalAttributes.isPresent() && optionalAttributes.get().contains(attributeJsonPointer)) {
            eventToPersist = AttributeModified.of(thingId, attributeJsonPointer, attributeValue, nextRevision,
                    eventTimestamp(), dittoHeaders);
            response = ModifyAttributeResponse.modified(thingId, attributeJsonPointer, dittoHeaders);
        } else {
            eventToPersist = AttributeCreated.of(thingId, attributeJsonPointer, attributeValue, nextRevision,
                    eventTimestamp(), dittoHeaders);
            response = ModifyAttributeResponse.created(thingId, attributeJsonPointer, attributeValue, dittoHeaders);
        }

        return newResult(eventToPersist, response);
    }
}
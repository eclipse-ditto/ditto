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

import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttribute;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributeResponse;
import org.eclipse.ditto.signals.events.things.AttributeDeleted;

/**
 * This strategy handles the {@link DeleteAttribute} command.
 */
@NotThreadSafe
public final class DeleteAttributeStrategy extends AbstractThingCommandStrategy<DeleteAttribute> {

    /**
     * Constructs a new {@code DeleteAttributeStrategy} object.
     */
    public DeleteAttributeStrategy() {
        super(DeleteAttribute.class);
    }

    @Override
    protected Result doApply(final Context context, final DeleteAttribute command) {
        final String thingId = context.getThingId();
        final Thing thing = context.getThing();
        final long nextRevision = context.nextRevision();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final JsonPointer attributeJsonPointer = command.getAttributePointer();
        final Optional<Attributes> attributesOptional = thing.getAttributes();
        if (attributesOptional.isPresent()) {
            final Attributes attributes = attributesOptional.get();
            if (attributes.contains(attributeJsonPointer)) {
                final AttributeDeleted attributeDeleted = AttributeDeleted.of(command.getThingId(),
                        attributeJsonPointer, nextRevision, eventTimestamp(), dittoHeaders);

                return result(attributeDeleted,
                        DeleteAttributeResponse.of(thingId, attributeJsonPointer, dittoHeaders));
            } else {
                return result(attributeNotFound(thingId, attributeJsonPointer, command.getDittoHeaders()));
            }
        } else {
            return result(attributeNotFound(thingId, attributeJsonPointer, command.getDittoHeaders()));
        }
    }
}
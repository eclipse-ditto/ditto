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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributeResponse;
import org.eclipse.ditto.signals.events.things.AttributeCreated;
import org.eclipse.ditto.signals.events.things.AttributeModified;

/**
 * This strategy handles the {@link ModifyAttribute} command.
 */
@Immutable
final class ModifyAttributeStrategy extends AbstractCommandStrategy<ModifyAttribute> {

    /**
     * Constructs a new {@code ModifyAttributeStrategy} object.
     */
    ModifyAttributeStrategy() {
        super(ModifyAttribute.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final ModifyAttribute command) {

        return getThingOrThrow(thing).getAttributes()
                .filter(attributes -> attributes.contains(command.getAttributePointer()))
                .map(attributes -> getModifyResult(context, nextRevision, command))
                .orElseGet(() -> getCreateResult(context, nextRevision, command));
    }

    private static Result getModifyResult(final Context context, final long nextRevision, final ModifyAttribute command) {
        final String thingId = context.getThingId();
        final JsonPointer attributePointer = command.getAttributePointer();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newResult(
                AttributeModified.of(thingId, attributePointer, command.getAttributeValue(), nextRevision,
                        getEventTimestamp(), dittoHeaders),
                ModifyAttributeResponse.modified(thingId, attributePointer, dittoHeaders));
    }

    private static Result getCreateResult(final Context context, final long nextRevision, final ModifyAttribute command) {
        final String thingId = context.getThingId();
        final JsonPointer attributePointer = command.getAttributePointer();
        final JsonValue attributeValue = command.getAttributeValue();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newResult(
                AttributeCreated.of(thingId, attributePointer, attributeValue, nextRevision,
                        getEventTimestamp(), dittoHeaders),
                ModifyAttributeResponse.created(thingId, attributePointer, attributeValue, dittoHeaders));
    }

}

/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.ThingCommandSizeValidator;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributeResponse;
import org.eclipse.ditto.signals.events.things.AttributeCreated;
import org.eclipse.ditto.signals.events.things.AttributeModified;

/**
 * This strategy handles the {@link ModifyAttribute} command.
 */
@Immutable
final class ModifyAttributeStrategy
        extends AbstractConditionalHeadersCheckingCommandStrategy<ModifyAttribute, JsonValue> {

    /**
     * Constructs a new {@code ModifyAttributeStrategy} object.
     */
    ModifyAttributeStrategy() {
        super(ModifyAttribute.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final ModifyAttribute command) {
        final Thing nonNullThing = getThingOrThrow(thing);

        ThingCommandSizeValidator.getInstance().ensureValidSize(() -> {
            final long lengthWithOutAttribute = nonNullThing.removeAttribute(command.getAttributePointer())
                    .toJsonString()
                    .length();
            final long attributeLength = command.getAttributeValue().toString().length()
                    + command.getAttributePointer().length() + 5L;
            return lengthWithOutAttribute + attributeLength;
        }, command::getDittoHeaders);

        return nonNullThing.getAttributes()
                .filter(attributes -> attributes.contains(command.getAttributePointer()))
                .map(attributes -> getModifyResult(context, nextRevision, command))
                .orElseGet(() -> getCreateResult(context, nextRevision, command));
    }

    private Result getModifyResult(final Context context, final long nextRevision,
            final ModifyAttribute command) {
        final String thingId = context.getThingId();
        final JsonPointer attributePointer = command.getAttributePointer();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newMutationResult(command,
                AttributeModified.of(thingId, attributePointer, command.getAttributeValue(), nextRevision,
                        getEventTimestamp(), dittoHeaders),
                ModifyAttributeResponse.modified(thingId, attributePointer, dittoHeaders), this);
    }

    private Result getCreateResult(final Context context, final long nextRevision,
            final ModifyAttribute command) {
        final String thingId = context.getThingId();
        final JsonPointer attributePointer = command.getAttributePointer();
        final JsonValue attributeValue = command.getAttributeValue();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newMutationResult(command,
                AttributeCreated.of(thingId, attributePointer, attributeValue, nextRevision,
                        getEventTimestamp(), dittoHeaders),
                ModifyAttributeResponse.created(thingId, attributePointer, attributeValue, dittoHeaders), this);
    }

    @Override
    public Optional<JsonValue> determineETagEntity(final ModifyAttribute command, @Nullable final Thing thing) {
        return getThingOrThrow(thing).getAttributes().flatMap(attrs -> attrs.getValue(command.getAttributePointer()));
    }
}

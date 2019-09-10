/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.things.ThingCommandSizeValidator;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributeResponse;
import org.eclipse.ditto.signals.events.things.AttributeCreated;
import org.eclipse.ditto.signals.events.things.AttributeModified;
import org.eclipse.ditto.signals.events.things.ThingEvent;

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
    protected Result<ThingEvent> doApply(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final ModifyAttribute command) {
        final Thing nonNullThing = getEntityOrThrow(thing);

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
                .map(attributes -> getModifyResult(context, nextRevision, command, thing))
                .orElseGet(() -> getCreateResult(context, nextRevision, command, thing));
    }

    private Result<ThingEvent> getModifyResult(final Context<ThingId> context, final long nextRevision,
            final ModifyAttribute command, @Nullable final Thing thing) {
        final ThingId thingId = context.getState();
        final JsonPointer attributePointer = command.getAttributePointer();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent event =
                AttributeModified.of(thingId, attributePointer, command.getAttributeValue(), nextRevision,
                        getEventTimestamp(), dittoHeaders);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyAttributeResponse.modified(thingId, attributePointer, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    private Result<ThingEvent> getCreateResult(final Context<ThingId> context, final long nextRevision,
            final ModifyAttribute command, @Nullable final Thing thing) {
        final ThingId thingId = context.getState();
        final JsonPointer attributePointer = command.getAttributePointer();
        final JsonValue attributeValue = command.getAttributeValue();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent event =
                AttributeCreated.of(thingId, attributePointer, attributeValue, nextRevision, getEventTimestamp(),
                        dittoHeaders);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyAttributeResponse.created(thingId, attributePointer, attributeValue, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    @Override
    public Optional<JsonValue> determineETagEntity(final ModifyAttribute command, @Nullable final Thing thing) {
        return Optional.of(command.getAttributeValue());
    }
}

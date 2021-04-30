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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributes;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributesResponse;
import org.eclipse.ditto.things.model.signals.events.AttributesCreated;
import org.eclipse.ditto.things.model.signals.events.AttributesModified;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link ModifyAttributes} command.
 */
@Immutable
final class ModifyAttributesStrategy extends AbstractThingCommandStrategy<ModifyAttributes> {

    /**
     * Constructs a new {@code ModifyAttributesStrategy} object.
     */
    ModifyAttributesStrategy() {
        super(ModifyAttributes.class);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final ModifyAttributes command,
            @Nullable final Metadata metadata) {

        final Thing nonNullThing = getEntityOrThrow(thing);

        final JsonObject thingWithoutAttributesJsonObject = nonNullThing.removeAttributes().toJson();
        final JsonObject attributesJsonObject = command.getAttributes().toJson();

        ThingCommandSizeValidator.getInstance().ensureValidSize(
                () -> {
                    final long lengthWithOutAttributes = thingWithoutAttributesJsonObject.getUpperBoundForStringSize();
                    final long attributesLength = attributesJsonObject.getUpperBoundForStringSize()
                            + "attributes".length() + 5L;
                    return lengthWithOutAttributes + attributesLength;
                },
                () -> {
                    final long lengthWithOutAttributes = thingWithoutAttributesJsonObject.toString().length();
                    final long attributesLength = attributesJsonObject.toString().length()
                            + "attributes".length() + 5L;
                    return lengthWithOutAttributes + attributesLength;
                },
                command::getDittoHeaders);

        return nonNullThing.getAttributes()
                .map(attributes -> getModifyResult(context, nextRevision, command, thing, metadata))
                .orElseGet(() -> getCreateResult(context, nextRevision, command, thing, metadata));
    }

    private Result<ThingEvent<?>> getModifyResult(final Context<ThingId> context, final long nextRevision,
            final ModifyAttributes command, @Nullable final Thing thing, @Nullable final Metadata metadata) {
        final ThingId thingId = context.getState();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent<?> event =
                AttributesModified.of(thingId, command.getAttributes(), nextRevision, getEventTimestamp(),
                        dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyAttributesResponse.modified(thingId, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    private Result<ThingEvent<?>> getCreateResult(final Context<ThingId> context, final long nextRevision,
            final ModifyAttributes command, @Nullable final Thing thing, @Nullable final Metadata metadata) {
        final ThingId thingId = context.getState();
        final Attributes attributes = command.getAttributes();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent<?> event =
                AttributesCreated.of(thingId, attributes, nextRevision, getEventTimestamp(), dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyAttributesResponse.created(thingId, attributes, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyAttributes command, @Nullable final Thing previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(Thing::getAttributes).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyAttributes command, @Nullable final Thing newEntity) {
        return Optional.of(command.getAttributes()).flatMap(EntityTag::fromEntity);
    }
}

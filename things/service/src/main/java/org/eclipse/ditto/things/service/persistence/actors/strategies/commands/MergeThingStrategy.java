/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonMergePatch;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;
import org.eclipse.ditto.things.model.signals.commands.ThingResourceMapper;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingMergeInvalidException;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThingResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;

/**
 * This strategy handles the {@link MergeThing} command for an already existing Thing.
 */
@Immutable
final class MergeThingStrategy extends AbstractThingCommandStrategy<MergeThing> {

    private static final ThingResourceMapper<Thing, Optional<EntityTag>> ENTITY_TAG_MAPPER =
            ThingResourceMapper.from(EntityTagCalculator.getInstance());

    /**
     * Constructs a new {@code MergeThingStrategy} object.
     */
    MergeThingStrategy() {
        super(MergeThing.class);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final MergeThing command,
            @Nullable final Metadata metadata) {

        final Thing nonNullThing = getEntityOrThrow(thing);
        final Instant eventTs = getEventTimestamp();
        return handleMergeExisting(context, nonNullThing, eventTs, nextRevision, command, metadata);
    }

    private Result<ThingEvent<?>> handleMergeExisting(final Context<ThingId> context, final Thing thing,
            final Instant eventTs, final long nextRevision, final MergeThing command,
            @Nullable final Metadata metadata) {
        return handleMergeExistingV2WithV2Command(context, thing, eventTs, nextRevision, command, metadata);
    }

    /**
     * Handles a {@link MergeThing} command that was sent via API v2 and targets a Thing with API version V2.
     */
    private Result<ThingEvent<?>> handleMergeExistingV2WithV2Command(final Context<ThingId> context, final Thing thing,
            final Instant eventTs, final long nextRevision, final MergeThing command,
            @Nullable final Metadata metadata) {
        return applyMergeCommand(context, thing, eventTs, nextRevision, command, metadata);
    }

    private Result<ThingEvent<?>> applyMergeCommand(final Context<ThingId> context, final Thing thing,
            final Instant eventTs, final long nextRevision, final MergeThing command,
            @Nullable final Metadata metadata) {

        // make sure that the ThingMerged-Event contains all data contained in the resulting existingThing
        // (this is required e.g. for updating the search-index)
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final JsonPointer path = command.getPath();
        final JsonValue value = command.getValue();

        final Thing mergedThing = wrapException(() -> mergeThing(context, command, thing, eventTs, nextRevision),
                command.getDittoHeaders());
        final ThingEvent<?> event =
                ThingMerged.of(command.getEntityId(), path, value, nextRevision, eventTs, dittoHeaders, metadata);
        final MergeThingResponse mergeThingResponse =
                MergeThingResponse.of(command.getEntityId(), path, dittoHeaders);

        final WithDittoHeaders response = appendETagHeaderIfProvided(command, mergeThingResponse, mergedThing);
        return ResultFactory.newMutationResult(command, event, response);
    }

    private Thing mergeThing(final Context<ThingId> context, final MergeThing command, final Thing thing,
            final Instant eventTs, final long nextRevision) {
        final JsonObject existingThingJson = thing.toJson(FieldType.all());
        final JsonMergePatch jsonMergePatch = JsonMergePatch.of(command.getPath(), command.getValue());
        final JsonObject mergedJson = jsonMergePatch.applyOn(existingThingJson).asObject();

        ThingCommandSizeValidator.getInstance().ensureValidSize(
                mergedJson::getUpperBoundForStringSize,
                () -> mergedJson.toString().length(),
                command::getDittoHeaders);

        context.getLog().debug("Result of JSON merge: {}", mergedJson);
        final Thing mergedThing = ThingsModelFactory.newThingBuilder(mergedJson)
                .setRevision(nextRevision)
                .setModified(eventTs).build();
        context.getLog().debug("Thing created from merged JSON: {}", mergedThing);
        return mergedThing;
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final MergeThing command, @Nullable final Thing previousEntity) {
        return ENTITY_TAG_MAPPER.map(command.getPath(), previousEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final MergeThing thingCommand, @Nullable final Thing newEntity) {
        return ENTITY_TAG_MAPPER.map(thingCommand.getPath(), getEntityOrThrow(newEntity));
    }

    private static <T> T wrapException(final Supplier<T> supplier, final DittoHeaders dittoHeaders) {
        try {
            return supplier.get();
        } catch (final JsonRuntimeException
                | IllegalArgumentException
                | NullPointerException
                | UnsupportedOperationException e) {
            throw ThingMergeInvalidException.fromMessage(e.getMessage(), dittoHeaders);
        }
    }
}

/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.entity.metadata.Metadata;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.base.CommandNotSupportedException;
import org.eclipse.ditto.signals.commands.things.ThingCommandSizeValidator;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingMergeInvalidException;
import org.eclipse.ditto.signals.commands.things.modify.MergeThing;
import org.eclipse.ditto.signals.commands.things.modify.MergeThingResponse;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingMerged;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.MergeThing} command for an already
 * existing Thing.
 */
@Immutable
final class MergeThingStrategy extends AbstractThingCommandStrategy<MergeThing> {

    MergeThingStrategy() {
        super(MergeThing.class);
    }

    @Override
    protected Result<ThingEvent> doApply(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision,
            final MergeThing command, @Nullable final Metadata metadata) {

        final Thing nonNullThing = getEntityOrThrow(thing);

        final JsonObject thingJsonObject = nonNullThing.toJson();
        ThingCommandSizeValidator.getInstance().ensureValidSize(
                thingJsonObject::getUpperBoundForStringSize,
                () -> thingJsonObject.toString().length(),
                command::getDittoHeaders);

        final Instant eventTs = getEventTimestamp();

        // merge is not supported for V1
        if (JsonSchemaVersion.V_1.equals(command.getImplementedSchemaVersion())) {
            return getUnsupportedSchemaVersionResult(command);
        }

        // from V2 upwards, use this logic:
        return handleMergeExistingWithV2Command(context, nonNullThing, eventTs, nextRevision, command, metadata);
    }

    private Result<ThingEvent> handleMergeExistingWithV2Command(final Context<ThingId> context, final Thing thing,
            final Instant eventTs, final long nextRevision, final MergeThing command,
            @Nullable final Metadata metadata) {
        if (JsonSchemaVersion.V_1.equals(thing.getImplementedSchemaVersion())) {
            return getUnsupportedSchemaVersionResult(command);
        } else {
            return handleMergeExistingV2WithV2Command(context, thing, eventTs, nextRevision, command, metadata);
        }
    }

    /**
     * Handles a {@link MergeThing} command that was sent via API v2 and targets a Thing with API version V2.
     */
    private Result<ThingEvent> handleMergeExistingV2WithV2Command(final Context<ThingId> context,
            final Thing thing, final Instant eventTs, final long nextRevision,
            final MergeThing command, @Nullable final Metadata metadata) {
        return applyMergeCommand(context, thing, eventTs, nextRevision, command, metadata);
    }

    private Result<ThingEvent> applyMergeCommand(final Context<ThingId> context, final Thing thing,
            final Instant eventTs, final long nextRevision, final MergeThing command,
            @Nullable final Metadata metadata) {

        // make sure that the ThingMerged-Event contains all data contained in the resulting existingThing (this is
        // required e.g. for updating the search-index)
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final JsonPointer path = command.getPath();
        final JsonValue value = command.getValue();

        final Thing mergedThing =
                wrapException(() -> mergeThing(command, thing, eventTs, nextRevision), command.getDittoHeaders());
        final ThingEvent<?> event =
                ThingMerged.of(command.getThingEntityId(), path, value, nextRevision, eventTs, dittoHeaders, metadata);
        final MergeThingResponse mergeThingResponse =
                MergeThingResponse.of(command.getThingEntityId(), path, value, dittoHeaders);

        final WithDittoHeaders<?> response = appendETagHeaderIfProvided(command, mergeThingResponse, mergedThing);
        return ResultFactory.newMutationResult(command, event, response);
    }

    private Thing mergeThing(final MergeThing command, final Thing thing, final Instant eventTs,
            final long nextRevision) {

        final ThingBuilder.FromCopy thingWithModifications = thing.toBuilder()
                .setModified(eventTs)
                .setRevision(nextRevision);

        final JsonObject jsonObject = thing.toJson();
        final JsonObject mergePatch = JsonFactory.newObject(command.getPath(), command.getValue());
        final JsonObject mergedJson = JsonFactory.newObjectWithoutNullValues(mergePatch, jsonObject);
        final Thing mergedThing = ThingsModelFactory.newThing(mergedJson);

        mergedThing.getPolicyEntityId().ifPresent(thingWithModifications::setPolicyId);
        mergedThing.getDefinition().ifPresent(thingWithModifications::setDefinition);
        mergedThing.getAttributes().ifPresent(thingWithModifications::setAttributes);
        mergedThing.getFeatures().ifPresent(thingWithModifications::setFeatures);

        return thingWithModifications.build();
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final MergeThing command, @Nullable final Thing previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final MergeThing thingCommand, @Nullable final Thing newEntity) {
        return Optional.of(getEntityOrThrow(newEntity)).flatMap(EntityTag::fromEntity);
    }

    private Result<ThingEvent> getUnsupportedSchemaVersionResult(final MergeThing command) {
        return ResultFactory.newErrorResult(
                CommandNotSupportedException.newBuilder(command.getImplementedSchemaVersion().toInt())
                        .dittoHeaders(command.getDittoHeaders())
                        .build(), command);
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

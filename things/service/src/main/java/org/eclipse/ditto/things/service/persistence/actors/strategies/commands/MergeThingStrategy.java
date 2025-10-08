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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.pekko.actor.ActorSystem;
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
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.things.service.common.config.DittoThingsConfig;
import org.eclipse.ditto.things.service.utils.PatchConditionsEvaluator;

/**
 * This strategy handles the {@link MergeThing} command for an already existing Thing.
 */
@Immutable
final class MergeThingStrategy extends AbstractThingModifyCommandStrategy<MergeThing> {

    private static final ThingResourceMapper<Thing, Optional<EntityTag>> ENTITY_TAG_MAPPER =
            ThingResourceMapper.from(EntityTagCalculator.getInstance());

    private final ActorSystem actorSystem;


    /**
     * Constructs a new {@code MergeThingStrategy} object.
     *
     * @param actorSystem the actor system to use for loading the WoT extension.
     */
    MergeThingStrategy(final ActorSystem actorSystem) {
        super(MergeThing.class, actorSystem);
        this.actorSystem = actorSystem;
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

    @Override
    protected CompletionStage<MergeThing> performWotValidation(final MergeThing command,
            @Nullable final Thing previousThing,
            @Nullable final Thing previewThing
    ) {
        return wotThingModelValidator.validateMergeThing(
                Optional.ofNullable(previewThing).flatMap(Thing::getDefinition)
                        .or(() -> Optional.ofNullable(previousThing).flatMap(Thing::getDefinition))
                        .orElse(null),
                command,
                Optional.ofNullable(previewThing).orElseThrow(),
                command.getResourcePath(),
                command.getDittoHeaders()
        ).thenApply(aVoid -> command);
    }

    private Result<ThingEvent<?>> handleMergeExisting(final Context<ThingId> context,
            final Thing thing,
            final Instant eventTs,
            final long nextRevision,
            final MergeThing command,
            @Nullable final Metadata metadata
    ) {
        return handleMergeExistingV2WithV2Command(context, thing, eventTs, nextRevision, command, metadata);
    }

    /**
     * Handles a {@link MergeThing} command that was sent via API v2 and targets a Thing with API version V2.
     */
    private Result<ThingEvent<?>> handleMergeExistingV2WithV2Command(final Context<ThingId> context, final Thing thing,
            final Instant eventTs,
            final long nextRevision,
            final MergeThing command,
            @Nullable final Metadata metadata
    ) {
        return applyMergeCommand(context, thing, eventTs, nextRevision, command, metadata);
    }

    private Result<ThingEvent<?>> applyMergeCommand(final Context<ThingId> context,
            final Thing thing,
            final Instant eventTs,
            final long nextRevision,
            final MergeThing command,
            @Nullable final Metadata metadata
    ) {
        // make sure that the ThingMerged-Event contains all data contained in the resulting existingThing
        // (this is required e.g. for updating the search-index)
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final JsonPointer path = command.getPath();
        final JsonValue originalValue = command.getEntity().orElseGet(command::getValue);
        final PatchConditionsEvaluator.PatchConditionResult patchResult = evaluatePatchConditionsWithResult(thing, originalValue, command);

        if (patchResult.isEmpty()) {
            final CompletionStage<WithDittoHeaders> responseStage = CompletableFuture.completedFuture(
                    appendETagHeaderIfProvided(command, MergeThingResponse.of(command.getEntityId(), path,
                            createCommandResponseDittoHeaders(dittoHeaders, nextRevision)), thing)
            );
            return ResultFactory.newQueryResult(command, responseStage);
        }

        final JsonValue filteredValue = patchResult.getFilteredValue();
        final Thing mergedThing = wrapException(() -> mergeThing(context, command, thing, eventTs, nextRevision, filteredValue),
                command.getDittoHeaders());

        final CompletionStage<MergeThing> validatedStage = buildValidatedStage(command, thing, mergedThing);

        final CompletionStage<ThingEvent<?>> eventStage = validatedStage.thenApply(mergeThing ->
                ThingMerged.of(mergeThing.getEntityId(), path, filteredValue, nextRevision, eventTs, dittoHeaders, metadata)
        );
        final CompletionStage<WithDittoHeaders> responseStage = validatedStage.thenApply(mergeThing ->
                appendETagHeaderIfProvided(mergeThing, MergeThingResponse.of(command.getEntityId(), path,
                                createCommandResponseDittoHeaders(dittoHeaders, nextRevision)), mergedThing)
        );
        return ResultFactory.newMutationResult(command, eventStage, responseStage);
    }

    private Thing mergeThing(final Context<ThingId> context,
            final MergeThing command,
            final Thing thing,
            final Instant eventTs,
            final long nextRevision,
            final JsonValue filteredValue
    ) {
        final JsonObject existingThingJson = thing.toJson(FieldType.all());
        
        final JsonMergePatch jsonMergePatch = JsonMergePatch.of(command.getPath(), filteredValue);
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

    /**
     * Evaluates patch conditions for a MergeThing command and returns the result with empty payload detection.
     * 
     * @param existingThing the current state of the Thing
     * @param mergeValue the original merge value to be filtered
     * @param command the MergeThing command containing patch conditions
     * @return the result containing the filtered merge value and whether it became empty
     * @since 3.8.0
     */
    PatchConditionsEvaluator.PatchConditionResult evaluatePatchConditionsWithResult(final Thing existingThing,
            final JsonValue mergeValue,
            final MergeThing command) {
        final DittoThingsConfig thingsConfig = DittoThingsConfig.of(
                DefaultScopedConfig.dittoScoped(actorSystem.settings().config())
        );
        final boolean removeEmptyObjects = thingsConfig.isMergeRemoveEmptyObjectsAfterPatchConditionFiltering();
        
        return PatchConditionsEvaluator.evaluatePatchConditionsWithResult(existingThing, mergeValue, command, removeEmptyObjects);
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

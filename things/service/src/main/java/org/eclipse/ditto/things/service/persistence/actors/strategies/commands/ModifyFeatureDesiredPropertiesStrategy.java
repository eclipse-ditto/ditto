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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDesiredProperties;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDesiredPropertiesResponse;
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertiesCreated;
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertiesModified;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDesiredProperties} command.
 */
@Immutable
final class ModifyFeatureDesiredPropertiesStrategy
        extends AbstractThingModifyCommandStrategy<ModifyFeatureDesiredProperties> {

    /**
     * Constructs a new {@code ModifyFeatureDesiredPropertiesStrategy} object.
     *
     * @param actorSystem the actor system to use for loading the WoT extension.
     */
    ModifyFeatureDesiredPropertiesStrategy(final ActorSystem actorSystem) {
        super(ModifyFeatureDesiredProperties.class, actorSystem);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final ModifyFeatureDesiredProperties command,
            @Nullable final Metadata metadata) {

        final String featureId = command.getFeatureId();

        final Thing nonNullThing = getEntityOrThrow(thing);

        final JsonObject thingWithoutDesiredProperties =
                nonNullThing.removeFeatureDesiredProperties(featureId).toJson();
        final JsonObject propertiesJsonObject = command.getDesiredProperties().toJson();

        ThingCommandSizeValidator.getInstance().ensureValidSize(
                () -> {
                    final long lengthWithOutProperties = thingWithoutDesiredProperties.getUpperBoundForStringSize();
                    final long propertiesLength = propertiesJsonObject.getUpperBoundForStringSize()
                            + "desiredProperties".length() + featureId.length() + 5L;
                    return lengthWithOutProperties + propertiesLength;
                },
                () -> {
                    final long lengthWithOutProperties = thingWithoutDesiredProperties.toString().length();
                    final long propertiesLength = propertiesJsonObject.toString().length()
                            + "desiredProperties".length() + featureId.length() + 5L;
                    return lengthWithOutProperties + propertiesLength;
                },
                command::getDittoHeaders);

        return extractFeature(command, nonNullThing)
                .map(feature -> getModifyOrCreateResult(feature, context, nextRevision, command, thing, metadata))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(context.getState(), featureId,
                                command.getDittoHeaders()), command));
    }

    @Override
    protected CompletionStage<ModifyFeatureDesiredProperties> performWotValidation(
            final ModifyFeatureDesiredProperties command,
            @Nullable final Thing previousThing,
            @Nullable final Thing previewThing
    ) {
        return wotThingModelValidator.validateFeatureProperties(
                Optional.ofNullable(previousThing).flatMap(Thing::getDefinition).orElse(null),
                Optional.ofNullable(previousThing)
                        .flatMap(Thing::getFeatures)
                        .flatMap(f -> f.getFeature(command.getFeatureId()))
                        .flatMap(Feature::getDefinition)
                        .orElse(null),
                command.getFeatureId(),
                command.getDesiredProperties(),
                true,
                command.getResourcePath(),
                command.getDittoHeaders()
        ).thenApply(aVoid -> command);
    }

    private Optional<Feature> extractFeature(final ModifyFeatureDesiredProperties command,
            @Nullable final Thing thing) {

        return Optional.ofNullable(thing)
                .flatMap(Thing::getFeatures)
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result<ThingEvent<?>> getModifyOrCreateResult(final Feature feature,
            final Context<ThingId> context,
            final long nextRevision,
            final ModifyFeatureDesiredProperties command,
            @Nullable final Thing thing,
            @Nullable final Metadata metadata) {

        return feature.getDesiredProperties()
                .map(properties -> getModifyResult(context, nextRevision, command, thing, metadata))
                .orElseGet(() -> getCreateResult(context, nextRevision, command, thing, metadata));
    }

    private Result<ThingEvent<?>> getModifyResult(final Context<ThingId> context,
            final long nextRevision,
            final ModifyFeatureDesiredProperties command,
            @Nullable final Thing thing,
            @Nullable final Metadata metadata) {

        final ThingId thingId = context.getState();
        final String featureId = command.getFeatureId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final CompletionStage<ModifyFeatureDesiredProperties> validatedStage = buildValidatedStage(command, thing);
        final CompletionStage<ThingEvent<?>> eventStage = validatedStage
                .thenApply(ModifyFeatureDesiredProperties::getDesiredProperties)
                .thenApply(desiredProperties ->
                        FeatureDesiredPropertiesModified.of(thingId, featureId, desiredProperties, nextRevision,
                                getEventTimestamp(), dittoHeaders, metadata)
                );
        final CompletionStage<WithDittoHeaders> responseStage =
                validatedStage.thenApply(modifyFeatureDesiredProperties ->
                        appendETagHeaderIfProvided(modifyFeatureDesiredProperties,
                                ModifyFeatureDesiredPropertiesResponse.modified(context.getState(), featureId,
                                        dittoHeaders),
                                thing)
                );

        return ResultFactory.newMutationResult(command, eventStage, responseStage);
    }

    private Result<ThingEvent<?>> getCreateResult(final Context<ThingId> context,
            final long nextRevision,
            final ModifyFeatureDesiredProperties command,
            @Nullable final Thing thing,
            @Nullable final Metadata metadata) {

        final ThingId thingId = context.getState();
        final String featureId = command.getFeatureId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final CompletionStage<ModifyFeatureDesiredProperties> validatedStage = buildValidatedStage(command, thing);
        final CompletionStage<ThingEvent<?>> eventStage = validatedStage
                .thenApply(ModifyFeatureDesiredProperties::getDesiredProperties)
                .thenApply(desiredProperties ->
                        FeatureDesiredPropertiesCreated.of(thingId, featureId, desiredProperties, nextRevision,
                                getEventTimestamp(), dittoHeaders, metadata)
                );
        final CompletionStage<WithDittoHeaders> responseStage =
                validatedStage.thenApply(modifyFeatureDesiredProperties ->
                        appendETagHeaderIfProvided(modifyFeatureDesiredProperties,
                                ModifyFeatureDesiredPropertiesResponse.created(thingId, featureId,
                                        modifyFeatureDesiredProperties.getDesiredProperties(),
                                        dittoHeaders),
                                thing)
                );

        return ResultFactory.newMutationResult(command, eventStage, responseStage);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyFeatureDesiredProperties command,
            @Nullable final Thing previousEntity) {

        return extractFeature(command, previousEntity).flatMap(Feature::getDesiredProperties)
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyFeatureDesiredProperties command,
            @Nullable final Thing newEntity) {

        return Optional.of(command.getDesiredProperties()).flatMap(EntityTag::fromEntity);
    }
}

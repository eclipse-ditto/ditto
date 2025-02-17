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
import java.util.concurrent.CompletableFuture;
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
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDesiredProperties;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDesiredPropertiesResponse;
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertiesDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDesiredProperties} command.
 */
@Immutable
final class DeleteFeatureDesiredPropertiesStrategy
        extends AbstractThingModifyCommandStrategy<DeleteFeatureDesiredProperties> {

    /**
     * Constructs a new {@code DeleteFeatureDesiredPropertiesStrategy} object.
     */
    DeleteFeatureDesiredPropertiesStrategy(final ActorSystem actorSystem) {
        super(DeleteFeatureDesiredProperties.class, actorSystem);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final DeleteFeatureDesiredProperties command,
            @Nullable final Metadata metadata) {

        return extractFeature(command, thing)
                .map(feature -> getDeleteFeatureDesiredPropertiesResult(feature, context, nextRevision, command, thing,
                        metadata))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(context.getState(), command.getFeatureId(),
                                command.getDittoHeaders()), command));
    }

    @Override
    protected CompletionStage<DeleteFeatureDesiredProperties> performWotValidation(
            final DeleteFeatureDesiredProperties command,
            @Nullable final Thing previousThing,
            @Nullable final Thing previewThing
    ) {
        // it is always ok to delete a feature's desired properties - no need to validate for e.g. required properties,
        //  as they do not apply for desired properties
        return CompletableFuture.completedFuture(command);
    }

    private Optional<Feature> extractFeature(final DeleteFeatureDesiredProperties command,
            final @Nullable Thing thing) {

        return getEntityOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result<ThingEvent<?>> getDeleteFeatureDesiredPropertiesResult(final Feature feature,
            final Context<ThingId> context,
            final long nextRevision,
            final DeleteFeatureDesiredProperties command,
            @Nullable final Thing thing,
            @Nullable final Metadata metadata) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final ThingId thingId = context.getState();
        final String featureId = feature.getId();

        return feature.getDesiredProperties()
                .map(desiredProperties -> {
                    final CompletionStage<DeleteFeatureDesiredProperties> validatedStage =
                            buildValidatedStage(command, thing);
                    final CompletionStage<ThingEvent<?>> eventStage =
                            validatedStage.thenApply(deleteFeatureDesiredProperties ->
                                    FeatureDesiredPropertiesDeleted.of(thingId, featureId, nextRevision,
                                            getEventTimestamp(),
                                            dittoHeaders, metadata)
                            );
                    final CompletionStage<WithDittoHeaders> responseStage = validatedStage
                            .thenApply(deleteFeatureDesiredProperties ->
                                    appendETagHeaderIfProvided(deleteFeatureDesiredProperties,
                                            DeleteFeatureDesiredPropertiesResponse.of(thingId, featureId,
                                                    createCommandResponseDittoHeaders(dittoHeaders, nextRevision)),
                                            thing)
                            );

                    return ResultFactory.newMutationResult(command, eventStage, responseStage);
                })
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureDesiredPropertiesNotFound(thingId, featureId, dittoHeaders), command));
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeleteFeatureDesiredProperties command,
            @Nullable final Thing previousEntity) {

        return extractFeature(command, previousEntity).flatMap(Feature::getDesiredProperties)
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeleteFeatureDesiredProperties command,
            @Nullable final Thing newEntity) {

        return Optional.empty();
    }

}
